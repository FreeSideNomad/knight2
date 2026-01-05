package com.knight.application.service.email;

/**
 * Email templates for OTP verification.
 */
public final class OtpEmailTemplates {

    private OtpEmailTemplates() {
        // Utility class
    }

    /**
     * Generate HTML content for OTP verification email.
     *
     * @param otpCode The 6-digit OTP code
     * @param expiresInSeconds Expiration time in seconds
     * @return HTML email content
     */
    public static String buildHtml(String otpCode, int expiresInSeconds) {
        int minutes = expiresInSeconds / 60;
        int seconds = expiresInSeconds % 60;
        String expiryText = minutes > 0
            ? (seconds > 0 ? minutes + " minute" + (minutes > 1 ? "s" : "") + " and " + seconds + " seconds"
                          : minutes + " minute" + (minutes > 1 ? "s" : ""))
            : seconds + " seconds";

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;">
                <table role="presentation" style="width: 100%%; border-collapse: collapse;">
                    <tr>
                        <td align="center" style="padding: 40px 0;">
                            <table role="presentation" style="width: 600px; border-collapse: collapse; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                                <tr>
                                    <td style="padding: 40px 40px 20px 40px;">
                                        <h1 style="margin: 0; font-size: 24px; color: #333333;">Email Verification</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 0 40px;">
                                        <p style="margin: 0 0 20px 0; font-size: 16px; color: #666666; line-height: 1.5;">
                                            Please use the following verification code to complete your request:
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 0 40px;">
                                        <div style="background-color: #f8f9fa; border-radius: 8px; padding: 24px; text-align: center; margin: 0 0 20px 0;">
                                            <span style="font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #333333; font-family: 'Courier New', monospace;">
                                                %s
                                            </span>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 0 40px 20px 40px;">
                                        <p style="margin: 0; font-size: 14px; color: #999999; line-height: 1.5;">
                                            This code will expire in <strong>%s</strong>.
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 0 40px 40px 40px;">
                                        <p style="margin: 0; font-size: 14px; color: #999999; line-height: 1.5;">
                                            If you didn't request this code, please ignore this email or contact support if you have concerns.
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 20px 40px; background-color: #f8f9fa; border-radius: 0 0 8px 8px;">
                                        <p style="margin: 0; font-size: 12px; color: #999999; text-align: center;">
                                            This is an automated message from Knight Platform. Please do not reply to this email.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(otpCode, expiryText);
    }

    /**
     * Generate plain text content for OTP verification email.
     *
     * @param otpCode The 6-digit OTP code
     * @param expiresInSeconds Expiration time in seconds
     * @return Plain text email content
     */
    public static String buildText(String otpCode, int expiresInSeconds) {
        int minutes = expiresInSeconds / 60;
        int seconds = expiresInSeconds % 60;
        String expiryText = minutes > 0
            ? (seconds > 0 ? minutes + " minute" + (minutes > 1 ? "s" : "") + " and " + seconds + " seconds"
                          : minutes + " minute" + (minutes > 1 ? "s" : ""))
            : seconds + " seconds";

        return """
            Email Verification
            ==================

            Please use the following verification code to complete your request:

            %s

            This code will expire in %s.

            If you didn't request this code, please ignore this email or contact support if you have concerns.

            ---
            This is an automated message from Knight Platform. Please do not reply to this email.
            """.formatted(otpCode, expiryText);
    }

    /**
     * Generate subject line for OTP verification email.
     *
     * @param otpCode The 6-digit OTP code
     * @return Email subject
     */
    public static String buildSubject(String otpCode) {
        return "Your verification code: " + otpCode;
    }
}
