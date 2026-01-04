// Login Application
// Handles:
// 1. Provisioned user onboarding: Password setup → MFA enrollment
// 2. Returning users: Password → MFA challenge
// Self-registration is NOT allowed - users must be provisioned by admin first

(function() {
    'use strict';

    // State
    let state = {
        email: '',
        userId: '',
        mfaToken: '',
        mfaSecret: '',
        oobCode: '',
        authReqId: '',  // CIBA auth request ID
        authenticatorId: '',
        enrolledAuthenticators: [],
        guardianPollingInterval: null,
        cibaPollingInterval: null,
        submitting: false,
        isReturningUser: false,
        isOnboarding: false,  // True when user needs MFA enrollment (password already set)
        loginCsrfToken: ''    // HMAC-signed CSRF token for login flow
    };

    // DOM Elements
    const screens = {
        email: document.getElementById('screen-email'),
        password: document.getElementById('screen-password'),
        mfaSelect: document.getElementById('screen-mfa-select'),
        mfaTotp: document.getElementById('screen-mfa-totp'),
        mfaGuardian: document.getElementById('screen-mfa-guardian'),
        login: document.getElementById('screen-login'),
        mfaChallengeSelect: document.getElementById('screen-mfa-challenge-select'),
        mfaChallengeTotp: document.getElementById('screen-mfa-challenge-totp'),
        mfaChallengeGuardian: document.getElementById('screen-mfa-challenge-guardian'),
        ciba: document.getElementById('screen-ciba'),
        forgotPassword: document.getElementById('screen-forgot-password'),
        forgotPasswordSent: document.getElementById('screen-forgot-password-sent'),
        success: document.getElementById('screen-success')
    };

    // Initialize
    document.addEventListener('DOMContentLoaded', init);

    async function init() {
        // Get pre-session CSRF token first
        await initLoginCsrfToken();
        setupEventListeners();
        setupMfaOtpInputs();
        storeReturnUrl();
    }

    // Initialize pre-session CSRF token (Lapis-style HMAC-signed token)
    // Token is returned in response body, key is set in HttpOnly cookie
    async function initLoginCsrfToken() {
        try {
            const response = await fetch('/api/csrf/login-token', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' }
            });
            if (response.ok) {
                const data = await response.json();
                if (data.token) {
                    state.loginCsrfToken = data.token;
                    console.log('Login CSRF token initialized');
                }
            } else {
                console.error('Failed to get login CSRF token');
            }
        } catch (error) {
            console.error('Error getting login CSRF token:', error);
        }
    }

    // Store return URL from query params
    function storeReturnUrl() {
        const params = new URLSearchParams(window.location.search);
        const returnTo = params.get('return_to');
        if (returnTo) {
            sessionStorage.setItem('return_to', returnTo);
        }
    }

    // Get return URL
    function getReturnUrl() {
        return sessionStorage.getItem('return_to') || '/';
    }

    // Event Listeners
    function setupEventListeners() {
        // Email form
        document.getElementById('email-form').addEventListener('submit', handleEmailSubmit);

        // Password setup form (onboarding)
        document.getElementById('password-form').addEventListener('submit', handlePasswordSubmit);

        // MFA enrollment - Guardian only (no TOTP)
        const selectGuardian = document.getElementById('select-guardian');
        if (selectGuardian) {
            selectGuardian.addEventListener('click', () => startMfaEnrollment('oob'));
        }

        // Login form (returning users)
        const loginForm = document.getElementById('login-form');
        if (loginForm) {
            loginForm.addEventListener('submit', handleLoginSubmit);
        }

        // Change user link
        const loginChangeUser = document.getElementById('login-change-user');
        if (loginChangeUser) {
            loginChangeUser.addEventListener('click', handleChangeEmail);
        }

        // MFA challenge TOTP form
        const mfaChallengeTotpForm = document.getElementById('mfa-challenge-totp-form');
        if (mfaChallengeTotpForm) {
            mfaChallengeTotpForm.addEventListener('submit', handleMfaChallengeTotpSubmit);
        }

        // Back to MFA selection (challenge)
        const mfaChallengeBackToSelect = document.getElementById('mfa-challenge-back-to-select');
        if (mfaChallengeBackToSelect) {
            mfaChallengeBackToSelect.addEventListener('click', handleBackToMfaChallengeSelect);
        }
        const guardianChallengeBackToSelect = document.getElementById('guardian-challenge-back-to-select');
        if (guardianChallengeBackToSelect) {
            guardianChallengeBackToSelect.addEventListener('click', handleBackToMfaChallengeSelect);
        }

        // CIBA - Use password instead
        const cibaUsePassword = document.getElementById('ciba-use-password');
        if (cibaUsePassword) {
            cibaUsePassword.addEventListener('click', handleCibaUsePassword);
        }

        // Forgot Password
        const forgotPassword = document.getElementById('forgot-password');
        if (forgotPassword) {
            forgotPassword.addEventListener('click', handleForgotPasswordClick);
        }

        const forgotPasswordForm = document.getElementById('forgot-password-form');
        if (forgotPasswordForm) {
            forgotPasswordForm.addEventListener('submit', handleForgotPasswordSubmit);
        }

        const forgotBackToLogin = document.getElementById('forgot-back-to-login');
        if (forgotBackToLogin) {
            forgotBackToLogin.addEventListener('click', handleBackToLogin);
        }

        const forgotSentBackToLogin = document.getElementById('forgot-sent-back-to-login');
        if (forgotSentBackToLogin) {
            forgotSentBackToLogin.addEventListener('click', handleBackToLogin);
        }

        const resendResetLink = document.getElementById('resend-reset-link');
        if (resendResetLink) {
            resendResetLink.addEventListener('click', handleResendResetLink);
        }
    }

    // MFA OTP Input Handling (for TOTP codes)
    function setupMfaOtpInputs() {
        document.querySelectorAll('.otp-input').forEach(input => {
            input.addEventListener('input', handleOtpInput);
            input.addEventListener('keydown', handleOtpKeydown);
            input.addEventListener('paste', handleOtpPaste);
        });
    }

    function handleOtpInput(e) {
        const input = e.target;
        const value = input.value;

        if (!/^\d*$/.test(value)) {
            input.value = '';
            return;
        }

        if (value && input.nextElementSibling && input.nextElementSibling.classList.contains('otp-input')) {
            input.nextElementSibling.focus();
        }
    }

    function handleOtpKeydown(e) {
        const input = e.target;
        if (e.key === 'Backspace' && !input.value && input.previousElementSibling && input.previousElementSibling.classList.contains('otp-input')) {
            input.previousElementSibling.focus();
        }
    }

    function handleOtpPaste(e) {
        e.preventDefault();
        const paste = (e.clipboardData || window.clipboardData).getData('text');
        const digits = paste.replace(/\D/g, '').slice(0, 6);

        const container = e.target.closest('.otp-container');
        const inputs = container.querySelectorAll('.otp-input');

        digits.split('').forEach((digit, i) => {
            if (inputs[i]) {
                inputs[i].value = digit;
            }
        });

        const lastIndex = Math.min(digits.length, inputs.length) - 1;
        if (lastIndex >= 0 && inputs[lastIndex + 1]) {
            inputs[lastIndex + 1].focus();
        } else if (inputs[lastIndex]) {
            inputs[lastIndex].focus();
        }
    }

    function getOtpValue(containerSelector) {
        const inputs = document.querySelectorAll(containerSelector + ' .otp-input');
        return Array.from(inputs).map(i => i.value).join('');
    }

    function clearOtpInputs(containerSelector) {
        document.querySelectorAll(containerSelector + ' .otp-input').forEach(input => {
            input.value = '';
        });
        const firstInput = document.querySelector(containerSelector + ' .otp-input');
        if (firstInput) firstInput.focus();
    }

    // Screen Navigation
    function showScreen(screenName) {
        Object.values(screens).forEach(screen => {
            if (screen) screen.classList.remove('active');
        });
        if (screens[screenName]) {
            screens[screenName].classList.add('active');
        }
    }

    // Loading State
    function setLoading(buttonId, loading) {
        const btn = document.getElementById(buttonId);
        if (!btn) return;

        const text = btn.querySelector('.btn-text');
        const spinner = btn.querySelector('.spinner');

        btn.disabled = loading;
        if (text) text.style.display = loading ? 'none' : 'inline';
        if (spinner) spinner.style.display = loading ? 'inline-block' : 'none';
    }

    // Messages
    function showError(elementId, message) {
        const el = document.getElementById(elementId);
        if (el) {
            el.textContent = message;
            el.style.display = 'block';
        }
    }

    function hideError(elementId) {
        const el = document.getElementById(elementId);
        if (el) {
            el.style.display = 'none';
        }
    }

    function showSuccess(elementId, message) {
        const el = document.getElementById(elementId);
        if (el) {
            el.textContent = message;
            el.style.display = 'block';
        }
    }

    // ============================================
    // API Calls
    // ============================================

    // Get pre-session CSRF token from state
    // Token is stored in JavaScript memory, key is in HttpOnly cookie
    function getLoginCsrfToken() {
        return state.loginCsrfToken || '';
    }

    // Build headers with CSRF token
    function getApiHeaders() {
        return {
            'Content-Type': 'application/json',
            'X-Login-CSRF-Token': getLoginCsrfToken()
        };
    }

    async function checkUser(email) {
        const response = await fetch('/api/user/check', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({ email })
        });
        return response.json();
    }

    async function completeOnboarding(userId, email, password) {
        const response = await fetch('/api/user/complete-onboarding', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({ user_id: userId, email: email, password: password })
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error_description || 'Failed to complete setup');
        }
        return data;
    }

    async function loginUser(email, password) {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({ email, password })
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error_description || 'Login failed');
        }
        return data;
    }

    async function cibaStart(userId, bindingMessage) {
        const response = await fetch('/api/auth/ciba-start', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({
                user_id: userId,
                binding_message: bindingMessage || 'Click approve to sign in to the DEMO app'
            })
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error_description || 'Failed to start CIBA');
        }
        return data;
    }

    async function cibaVerify(authReqId, email) {
        const response = await fetch('/api/auth/ciba-verify', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({
                auth_req_id: authReqId,
                email: email
            })
        });
        return response.json();
    }

    async function associateMfa(mfaToken, authenticatorType) {
        const response = await fetch('/api/mfa/associate', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({
                mfa_token: mfaToken,
                authenticator_type: authenticatorType || 'otp'
            })
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error_description || 'Failed to start MFA enrollment');
        }
        return data;
    }

    async function verifyMfaEnrollment(mfaToken, otp, email, authenticatorType) {
        const response = await fetch('/api/mfa/verify', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({
                mfa_token: mfaToken,
                otp: otp,
                email: email,
                authenticator_type: authenticatorType || 'otp'
            })
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error_description || 'Invalid MFA code');
        }
        return data;
    }

    async function pollGuardianEnrollment(mfaToken, oobCode) {
        const response = await fetch('/api/mfa/challenge', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({
                mfa_token: mfaToken,
                oob_code: oobCode,
                email: state.email
            })
        });
        return response.json();
    }

    async function sendMfaChallenge(mfaToken, authenticatorId, authenticatorType) {
        const response = await fetch('/api/mfa/send-challenge', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({
                mfa_token: mfaToken,
                authenticator_id: authenticatorId,
                authenticator_type: authenticatorType
            })
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error_description || 'Failed to send MFA challenge');
        }
        return data;
    }

    async function verifyMfaChallenge(mfaToken, otp, authenticatorType, oobCode) {
        const body = {
            mfa_token: mfaToken,
            otp: otp,
            authenticator_type: authenticatorType,
            email: state.email
        };
        if (oobCode) body.oob_code = oobCode;

        const response = await fetch('/api/mfa/verify-challenge', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify(body)
        });
        const data = await response.json();
        if (!response.ok && data.error !== 'authorization_pending' && data.error !== 'slow_down') {
            throw new Error(data.error_description || 'Invalid MFA code');
        }
        return data;
    }

    async function markOnboardingComplete(userId) {
        const response = await fetch('/api/user/mark-onboarding-complete', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({ user_id: userId })
        });
        return response.json();
    }

    async function sendPasswordReset(email) {
        const response = await fetch('/api/auth/forgot-password', {
            method: 'POST',
            headers: getApiHeaders(),
            body: JSON.stringify({ email })
        });
        const data = await response.json();
        if (!response.ok && data.error) {
            throw new Error(data.error_description || data.error || 'Failed to send reset link');
        }
        return data;
    }

    // ============================================
    // Session Management
    // ============================================

    function setSessionAndRedirect(sessionId) {
        // Set session cookie (cookie name is CLIENT_SESSION to match nginx.conf)
        document.cookie = 'CLIENT_SESSION=' + sessionId + '; path=/; max-age=1200; samesite=lax';

        const returnTo = getReturnUrl();
        window.location.href = returnTo;
    }

    // ============================================
    // Email Submit Handler
    // ============================================

    async function handleEmailSubmit(e) {
        e.preventDefault();
        if (state.submitting) return;

        hideError('email-error');

        const email = document.getElementById('email').value.trim();
        if (!email) {
            showError('email-error', 'Please enter your email address');
            return;
        }

        state.submitting = true;
        setLoading('email-submit', true);

        try {
            const userStatus = await checkUser(email);
            console.log('User status:', userStatus);

            if (!userStatus.exists) {
                // User not provisioned - block self-registration
                showError('email-error', 'Your account has not been set up yet. Please contact your administrator.');
                return;
            }

            state.email = email;
            state.userId = userStatus.user_id;

            if (userStatus.onboarding_complete || userStatus.has_mfa) {
                // Returning user (or user who completed MFA but flag wasn't set)
                state.isReturningUser = true;
                state.isOnboarding = false;
                state.enrolledAuthenticators = userStatus.authenticators || [];

                // If has_mfa but not onboarding_complete, mark it complete now
                if (userStatus.has_mfa && !userStatus.onboarding_complete) {
                    console.log('User has MFA but onboarding_complete is false, marking complete');
                    markOnboardingComplete(userStatus.user_id).catch(e => console.error('Failed to mark complete:', e));
                }

                // All returning users use password login
                console.log('Returning user, showing password screen');
                showLoginScreen();
            } else if (userStatus.has_password) {
                // User already set password but hasn't completed MFA enrollment
                // Authenticate them and then show MFA enrollment
                state.isOnboarding = true;
                state.isReturningUser = false;
                state.enrolledAuthenticators = userStatus.authenticators || [];
                console.log('Onboarding user with existing password - showing login screen');
                showLoginScreen();
            } else {
                // Provisioned user who hasn't set password yet
                // Go directly to password setup (no OTP needed - admin provisioned)
                state.isOnboarding = true;
                state.isReturningUser = false;
                console.log('Provisioned user, needs onboarding - showing password setup');
                showScreen('password');
                document.getElementById('password').focus();
            }
        } catch (error) {
            showError('email-error', error.message);
        } finally {
            state.submitting = false;
            setLoading('email-submit', false);
        }
    }

    // ============================================
    // Login Screen (Returning Users)
    // ============================================

    function showLoginScreen() {
        document.getElementById('login-user-email').textContent = state.email;
        document.getElementById('login-user-initial').textContent = state.email.charAt(0).toUpperCase();
        showScreen('login');
        document.getElementById('login-password').focus();
    }

    async function handleLoginSubmit(e) {
        e.preventDefault();
        if (state.submitting) return;

        hideError('login-error');

        const password = document.getElementById('login-password').value;
        if (!password) {
            showError('login-error', 'Please enter your password');
            return;
        }

        state.submitting = true;
        setLoading('login-submit', true);

        try {
            const result = await loginUser(state.email, password);
            console.log('Login result:', result);

            if (result.mfa_required && result.mfa_token) {
                state.mfaToken = result.mfa_token;
                state.enrolledAuthenticators = result.authenticators || [];

                if (state.isOnboarding) {
                    // Onboarding user - go to MFA enrollment
                    console.log('Onboarding user authenticated, showing MFA enrollment');
                    showScreen('mfaSelect');
                } else {
                    // Returning user - initiate Guardian challenge
                    console.log('MFA required, initiating Guardian challenge');

                    // Find Guardian authenticator
                    const guardianAuth = state.enrolledAuthenticators.find(
                        a => a.type === 'oob' || a.type === 'push' || a.oob_channel === 'auth0'
                    );

                    if (guardianAuth) {
                        await initiateMfaChallenge(guardianAuth);
                    } else if (state.enrolledAuthenticators.length > 0) {
                        // Fallback to first authenticator
                        await initiateMfaChallenge(state.enrolledAuthenticators[0]);
                    } else {
                        throw new Error('No MFA authenticator found. Please contact support.');
                    }
                }
            } else if (result.authenticated && result.session_id) {
                showScreen('success');
                document.getElementById('success-message').textContent = 'Signed in successfully!';
                setTimeout(() => {
                    setSessionAndRedirect(result.session_id);
                }, 1000);
            } else {
                throw new Error('Unexpected response');
            }
        } catch (error) {
            showError('login-error', error.message);
        } finally {
            state.submitting = false;
            setLoading('login-submit', false);
        }
    }

    // ============================================
    // CIBA Login (Guardian users - passwordless)
    // ============================================

    async function startCibaLogin() {
        try {
            console.log('Starting CIBA login for user:', state.userId);

            const cibaResult = await cibaStart(state.userId);
            console.log('CIBA started:', cibaResult);

            state.authReqId = cibaResult.auth_req_id;

            // Set user info on CIBA screen
            document.getElementById('ciba-user-email').textContent = state.email;
            document.getElementById('ciba-user-initial').textContent = state.email.charAt(0).toUpperCase();

            // Show CIBA waiting screen
            showScreen('ciba');
            const statusEl = document.getElementById('ciba-status');
            if (statusEl) statusEl.textContent = 'Waiting for approval on your phone...';

            // Start polling
            startCibaPolling(cibaResult.interval || 5);
        } catch (error) {
            console.error('CIBA start error:', error);
            showError('email-error', error.message);
            showScreen('email');
        }
    }

    function startCibaPolling(interval) {
        stopCibaPolling();

        let attempts = 0;
        const maxAttempts = 60; // 5 minutes with 5s interval

        state.cibaPollingInterval = setInterval(async () => {
            attempts++;

            if (attempts > maxAttempts) {
                stopCibaPolling();
                showError('ciba-error', 'Request timed out. Please try again.');
                return;
            }

            try {
                const result = await cibaVerify(state.authReqId, state.email);
                console.log('CIBA poll result:', result);

                if (result.authenticated && result.session_id) {
                    stopCibaPolling();
                    showScreen('success');
                    document.getElementById('success-message').textContent = 'Signed in successfully!';
                    setTimeout(() => {
                        setSessionAndRedirect(result.session_id);
                    }, 1000);
                } else if (result.pending) {
                    // Still waiting
                    const statusEl = document.getElementById('ciba-status');
                    if (statusEl) statusEl.textContent = 'Waiting for approval on your phone...';
                } else if (result.error && result.error !== 'authorization_pending' && result.error !== 'slow_down') {
                    stopCibaPolling();
                    showError('ciba-error', result.error_description || 'Authentication failed');
                }
            } catch (error) {
                console.error('CIBA polling error:', error);
            }
        }, interval * 1000);
    }

    function stopCibaPolling() {
        if (state.cibaPollingInterval) {
            clearInterval(state.cibaPollingInterval);
            state.cibaPollingInterval = null;
        }
    }

    function handleCibaUsePassword(e) {
        e.preventDefault();
        stopCibaPolling();
        hideError('ciba-error');
        showLoginScreen();
    }

    // ============================================
    // MFA Challenge (Legacy - kept for compatibility)
    // ============================================

    async function startMfaChallenge() {
        const authenticators = state.enrolledAuthenticators;
        console.log('Enrolled authenticators:', authenticators);

        if (!authenticators || authenticators.length === 0) {
            showError('login-error', 'No MFA methods enrolled. Please contact support.');
            return;
        }

        if (authenticators.length === 1) {
            // Only one method - go directly to it
            await initiateMfaChallenge(authenticators[0]);
        } else {
            // Multiple methods - show selection
            showMfaChallengeSelection(authenticators);
        }
    }

    function showMfaChallengeSelection(authenticators) {
        const container = document.getElementById('mfa-challenge-options');
        container.innerHTML = '';

        authenticators.forEach(auth => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'mfa-option-btn';

            let icon = '🔐';
            let title = 'Authenticator App';
            let desc = 'Enter a code from your authenticator app';

            // Guardian can be type 'oob', 'push', or have oob_channel 'auth0'
            if (auth.type === 'oob' || auth.type === 'push' || auth.oob_channel === 'auth0') {
                icon = '📱';
                title = 'Auth0 Guardian';
                desc = 'Approve with push notification';
            }

            btn.innerHTML = `
                <div class="mfa-option-icon">${icon}</div>
                <div class="mfa-option-content">
                    <div class="mfa-option-title">${title}</div>
                    <div class="mfa-option-desc">${desc}</div>
                </div>
            `;

            btn.addEventListener('click', () => initiateMfaChallenge(auth));
            container.appendChild(btn);
        });

        showScreen('mfaChallengeSelect');
    }

    async function initiateMfaChallenge(authenticator) {
        console.log('Initiating MFA challenge for:', authenticator);
        state.authenticatorId = authenticator.id;

        try {
            // Guardian can be type 'oob', 'push', or have oob_channel 'auth0'
            if (authenticator.type === 'oob' || authenticator.type === 'push' || authenticator.oob_channel === 'auth0') {
                // Guardian push - send challenge and poll
                const challenge = await sendMfaChallenge(state.mfaToken, authenticator.id, 'oob');
                state.oobCode = challenge.oob_code;
                showScreen('mfaChallengeGuardian');

                // Show back button if multiple methods
                if (state.enrolledAuthenticators.length > 1) {
                    document.getElementById('mfa-challenge-guardian-back').style.display = 'block';
                }

                startGuardianChallengePolling();
            } else {
                // TOTP - just show the code entry screen
                showScreen('mfaChallengeTotp');
                clearOtpInputs('#mfa-challenge-otp-container');

                // Setup OTP inputs
                document.querySelectorAll('.mfa-challenge-otp-input').forEach(input => {
                    input.addEventListener('input', handleOtpInput);
                    input.addEventListener('keydown', handleOtpKeydown);
                    input.addEventListener('paste', handleOtpPaste);
                });

                // Show back button if multiple methods
                if (state.enrolledAuthenticators.length > 1) {
                    document.getElementById('mfa-challenge-totp-back').style.display = 'block';
                }
            }
        } catch (error) {
            console.error('MFA challenge error:', error);
            showError('mfa-challenge-select-error', error.message);
            showScreen('mfaChallengeSelect');
        }
    }

    function startGuardianChallengePolling() {
        stopGuardianPolling();

        const statusEl = document.getElementById('guardian-challenge-status');
        if (statusEl) statusEl.textContent = 'Waiting for approval...';

        let attempts = 0;
        const maxAttempts = 60;

        state.guardianPollingInterval = setInterval(async () => {
            attempts++;

            if (attempts > maxAttempts) {
                stopGuardianPolling();
                showError('mfa-challenge-guardian-error', 'Request timed out. Please try again.');
                return;
            }

            try {
                const result = await verifyMfaChallenge(state.mfaToken, null, 'oob', state.oobCode);
                console.log('Guardian challenge poll result:', result);

                if (result.authenticated && result.session_id) {
                    stopGuardianPolling();
                    showScreen('success');
                    document.getElementById('success-message').textContent = 'Signed in successfully!';
                    setTimeout(() => {
                        setSessionAndRedirect(result.session_id);
                    }, 1000);
                } else if (result.error === 'authorization_pending') {
                    if (statusEl) statusEl.textContent = 'Waiting for approval...';
                } else if (result.error === 'slow_down') {
                    console.log('Guardian: slow_down received');
                } else if (result.error) {
                    stopGuardianPolling();
                    showError('mfa-challenge-guardian-error', result.error_description || 'Verification failed');
                }
            } catch (error) {
                console.error('Guardian polling error:', error);
            }
        }, 2000);
    }

    async function handleMfaChallengeTotpSubmit(e) {
        e.preventDefault();
        if (state.submitting) return;

        hideError('mfa-challenge-totp-error');

        const code = getOtpValue('#mfa-challenge-otp-container');
        if (code.length !== 6) {
            showError('mfa-challenge-totp-error', 'Please enter the 6-digit code');
            return;
        }

        state.submitting = true;
        setLoading('mfa-challenge-totp-submit', true);

        try {
            const result = await verifyMfaChallenge(state.mfaToken, code, 'otp');
            console.log('MFA challenge result:', result);

            if (result.authenticated && result.session_id) {
                showScreen('success');
                document.getElementById('success-message').textContent = 'Signed in successfully!';
                setTimeout(() => {
                    setSessionAndRedirect(result.session_id);
                }, 1000);
            } else {
                throw new Error('Verification failed');
            }
        } catch (error) {
            showError('mfa-challenge-totp-error', error.message);
            clearOtpInputs('#mfa-challenge-otp-container');
        } finally {
            state.submitting = false;
            setLoading('mfa-challenge-totp-submit', false);
        }
    }

    function handleBackToMfaChallengeSelect(e) {
        e.preventDefault();
        stopGuardianPolling();
        hideError('mfa-challenge-totp-error');
        hideError('mfa-challenge-guardian-error');
        showMfaChallengeSelection(state.enrolledAuthenticators);
    }

    // ============================================
    // Password Setup (Onboarding)
    // ============================================

    async function handlePasswordSubmit(e) {
        e.preventDefault();
        if (state.submitting) return;

        hideError('password-error');

        const password = document.getElementById('password').value;
        const confirmPassword = document.getElementById('confirm-password').value;

        if (!password || password.length < 12) {
            showError('password-error', 'Password must be at least 12 characters');
            return;
        }

        if (password !== confirmPassword) {
            showError('password-error', 'Passwords do not match');
            return;
        }

        if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password) || !/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
            showError('password-error', 'Password must contain uppercase, lowercase, numbers, and a special character');
            return;
        }

        state.submitting = true;
        setLoading('password-submit', true);

        try {
            const result = await completeOnboarding(state.userId, state.email, password);
            console.log('Onboarding result:', result);

            if (result.mfa_required) {
                state.mfaToken = result.mfa_token;
                showScreen('mfaSelect');
            } else if (result.requires_login) {
                // Password set but auth failed - redirect to login
                console.log('Password set, redirecting to login');
                showScreen('login');
                document.getElementById('login-password').focus();
            } else if (result.access_token || (result.authenticated && result.session_id)) {
                // No MFA required - mark onboarding complete
                try {
                    await markOnboardingComplete(state.userId);
                    console.log('Onboarding marked complete');
                } catch (e) {
                    console.error('Failed to mark onboarding complete:', e);
                }

                if (result.session_id) {
                    showScreen('success');
                    document.getElementById('success-message').textContent = 'Setup complete! Redirecting...';
                    setTimeout(() => {
                        setSessionAndRedirect(result.session_id);
                    }, 1000);
                } else {
                    // Got tokens but no session - need to login again
                    console.log('Password set successfully, please login');
                    showScreen('login');
                    document.getElementById('login-password').focus();
                }
            } else if (result.error) {
                // Handle specific error messages
                let errorMsg = result.error_description || result.error;
                // Clean up Auth0 error messages
                if (errorMsg.includes('PasswordHistoryError:')) {
                    errorMsg = errorMsg.replace('PasswordHistoryError:', '').trim();
                }
                throw new Error(errorMsg);
            } else {
                console.error('Unexpected response:', result);
                throw new Error('Unexpected response from server');
            }
        } catch (error) {
            showError('password-error', error.message);
        } finally {
            state.submitting = false;
            setLoading('password-submit', false);
        }
    }

    // ============================================
    // MFA Enrollment (Onboarding)
    // ============================================

    async function startMfaEnrollment(authenticatorType) {
        try {
            console.log('Starting MFA enrollment with type:', authenticatorType);
            hideError('mfa-select-error');

            const enrollment = await associateMfa(state.mfaToken, authenticatorType);
            console.log('MFA enrollment started:', enrollment);

            if (authenticatorType === 'otp') {
                await showTotpEnrollment(enrollment);
            } else if (authenticatorType === 'oob') {
                await showGuardianEnrollment(enrollment);
            }
        } catch (error) {
            console.error('MFA enrollment error:', error);
            showError('mfa-select-error', 'Failed to start MFA enrollment: ' + error.message);
        }
    }

    async function showTotpEnrollment(enrollment) {
        if (enrollment.barcode_uri) {
            const qrContainer = document.getElementById('mfa-qr-code');
            qrContainer.innerHTML = '';

            if (typeof QRCode !== 'undefined') {
                try {
                    new QRCode(qrContainer, {
                        text: enrollment.barcode_uri,
                        width: 200,
                        height: 200,
                        colorDark: '#000000',
                        colorLight: '#ffffff',
                        correctLevel: QRCode.CorrectLevel.M
                    });
                } catch (error) {
                    console.error('QR code generation error:', error);
                    qrContainer.innerHTML = '<p>Could not generate QR code</p>';
                }
            } else {
                qrContainer.innerHTML = '<p>QR code library not loaded</p>';
            }
        }

        if (enrollment.secret) {
            state.mfaSecret = enrollment.secret;
            document.getElementById('mfa-secret').textContent = enrollment.secret;
        }

        showScreen('mfaTotp');
        clearOtpInputs('#mfa-otp-container');

        document.querySelectorAll('.mfa-otp-input').forEach(input => {
            input.addEventListener('input', handleOtpInput);
            input.addEventListener('keydown', handleOtpKeydown);
            input.addEventListener('paste', handleOtpPaste);
        });
    }

    async function showGuardianEnrollment(enrollment) {
        console.log('Guardian enrollment data:', enrollment);

        if (enrollment.barcode_uri) {
            const qrContainer = document.getElementById('guardian-qr-code');
            qrContainer.innerHTML = '';

            if (typeof QRCode !== 'undefined') {
                try {
                    new QRCode(qrContainer, {
                        text: enrollment.barcode_uri,
                        width: 200,
                        height: 200,
                        colorDark: '#000000',
                        colorLight: '#ffffff',
                        correctLevel: QRCode.CorrectLevel.M
                    });
                } catch (error) {
                    console.error('QR code generation error:', error);
                    qrContainer.innerHTML = '<p>Could not generate QR code</p>';
                }
            } else {
                qrContainer.innerHTML = '<p>QR code library not loaded</p>';
            }
        }

        if (enrollment.oob_code) {
            state.oobCode = enrollment.oob_code;
        }

        showScreen('mfaGuardian');
        startGuardianEnrollmentPolling();
    }

    function startGuardianEnrollmentPolling() {
        stopGuardianPolling();

        const statusEl = document.getElementById('guardian-status');
        if (statusEl) statusEl.textContent = 'Waiting for enrollment...';

        let attempts = 0;
        const maxAttempts = 60;

        state.guardianPollingInterval = setInterval(async () => {
            attempts++;

            if (attempts > maxAttempts) {
                stopGuardianPolling();
                showError('mfa-guardian-error', 'Enrollment timed out. Please try again.');
                return;
            }

            try {
                const result = await pollGuardianEnrollment(state.mfaToken, state.oobCode);
                console.log('Guardian poll result:', result);

                if (result.authenticated && result.session_id) {
                    stopGuardianPolling();

                    // Mark onboarding complete
                    try {
                        await markOnboardingComplete(state.userId);
                        console.log('Onboarding marked complete');
                    } catch (e) {
                        console.error('Failed to mark onboarding complete:', e);
                    }

                    showScreen('success');
                    document.getElementById('success-message').textContent = 'Setup complete! Redirecting...';
                    setTimeout(() => {
                        setSessionAndRedirect(result.session_id);
                    }, 1000);
                } else if (result.error === 'authorization_pending') {
                    if (statusEl) statusEl.textContent = 'Waiting for approval in Guardian app...';
                } else if (result.error === 'slow_down') {
                    console.log('Guardian: slow_down received');
                } else if (result.error) {
                    stopGuardianPolling();
                    showError('mfa-guardian-error', result.error_description || 'Enrollment failed');
                }
            } catch (error) {
                console.error('Guardian polling error:', error);
            }
        }, 2000);
    }

    function stopGuardianPolling() {
        if (state.guardianPollingInterval) {
            clearInterval(state.guardianPollingInterval);
            state.guardianPollingInterval = null;
        }
    }

    function handleBackToMfaSelect(e) {
        e.preventDefault();
        stopGuardianPolling();
        hideError('mfa-totp-error');
        hideError('mfa-guardian-error');
        showScreen('mfaSelect');
    }

    async function handleMfaTotpSubmit(e) {
        e.preventDefault();
        if (state.submitting) return;

        hideError('mfa-totp-error');

        const code = getOtpValue('#mfa-otp-container');
        if (code.length !== 6) {
            showError('mfa-totp-error', 'Please enter the 6-digit code from your authenticator app');
            return;
        }

        state.submitting = true;
        setLoading('mfa-totp-submit', true);

        try {
            const result = await verifyMfaEnrollment(state.mfaToken, code, state.email, 'otp');
            console.log('MFA verification result:', result);

            if (result.authenticated && result.session_id) {
                // Mark onboarding complete
                try {
                    await markOnboardingComplete(state.userId);
                    console.log('Onboarding marked complete');
                } catch (e) {
                    console.error('Failed to mark onboarding complete:', e);
                }

                showScreen('success');
                document.getElementById('success-message').textContent = 'Setup complete! Redirecting...';
                setTimeout(() => {
                    setSessionAndRedirect(result.session_id);
                }, 1000);
            } else {
                throw new Error('Unexpected response from MFA verification');
            }
        } catch (error) {
            showError('mfa-totp-error', error.message);
            clearOtpInputs('#mfa-otp-container');
        } finally {
            state.submitting = false;
            setLoading('mfa-totp-submit', false);
        }
    }

    // ============================================
    // Common Handlers
    // ============================================

    function handleChangeEmail(e) {
        e.preventDefault();
        stopGuardianPolling();

        state.email = '';
        state.userId = '';
        state.mfaToken = '';
        state.oobCode = '';
        state.isReturningUser = false;
        state.enrolledAuthenticators = [];

        document.getElementById('email').value = '';
        const loginPassword = document.getElementById('login-password');
        if (loginPassword) loginPassword.value = '';
        showScreen('email');
    }

    // ============================================
    // Forgot Password Handlers
    // ============================================

    function handleForgotPasswordClick(e) {
        e.preventDefault();
        hideError('login-error');

        // Pre-fill email if user was on login screen
        const forgotEmailInput = document.getElementById('forgot-email');
        if (forgotEmailInput && state.email) {
            forgotEmailInput.value = state.email;
        }

        showScreen('forgotPassword');
        if (forgotEmailInput) {
            forgotEmailInput.focus();
        }
    }

    async function handleForgotPasswordSubmit(e) {
        e.preventDefault();
        if (state.submitting) return;

        hideError('forgot-password-error');

        const email = document.getElementById('forgot-email').value.trim();
        if (!email) {
            showError('forgot-password-error', 'Please enter your email address');
            return;
        }

        state.submitting = true;
        setLoading('forgot-password-submit', true);

        try {
            await sendPasswordReset(email);

            // Store email for resend functionality
            state.forgotPasswordEmail = email;

            // Show success screen
            document.getElementById('forgot-sent-email').textContent = email;
            showScreen('forgotPasswordSent');

        } catch (error) {
            // For security, always show success even if email doesn't exist
            // This prevents email enumeration attacks
            console.log('Password reset request:', error.message);

            // Still show success to not reveal if email exists
            state.forgotPasswordEmail = email;
            document.getElementById('forgot-sent-email').textContent = email;
            showScreen('forgotPasswordSent');

        } finally {
            state.submitting = false;
            setLoading('forgot-password-submit', false);
        }
    }

    function handleBackToLogin(e) {
        e.preventDefault();
        hideError('forgot-password-error');

        // Clear forgot password form
        const forgotEmailInput = document.getElementById('forgot-email');
        if (forgotEmailInput) {
            forgotEmailInput.value = '';
        }

        // If we have a remembered email, go back to login screen
        if (state.email) {
            showLoginScreen();
        } else {
            showScreen('email');
        }
    }

    async function handleResendResetLink(e) {
        e.preventDefault();

        const email = state.forgotPasswordEmail;
        if (!email) {
            showScreen('forgotPassword');
            return;
        }

        try {
            await sendPasswordReset(email);
            // Show brief feedback
            const sentEmail = document.getElementById('forgot-sent-email');
            if (sentEmail) {
                const originalText = sentEmail.textContent;
                sentEmail.textContent = 'Email resent!';
                setTimeout(() => {
                    sentEmail.textContent = originalText;
                }, 2000);
            }
        } catch (error) {
            console.log('Resend password reset:', error.message);
        }
    }

})();
