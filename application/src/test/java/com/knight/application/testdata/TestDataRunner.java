package com.knight.application.testdata;

import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.types.ClientType;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.platform.sharedkernel.*;
import com.github.javafaker.Faker;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Spring Boot test runner for generating test data.
 * Uses domain layer for correctness - Client and ClientAccount aggregates
 * are created via their factory methods, ensuring all business rules are applied.
 *
 * Generates 10,000 clients with skewed account distribution:
 * - Most clients have 3-10 accounts (heavily weighted)
 * - Only ~5% have more than 100 accounts (up to 1000)
 * - SRF clients are Canadian, CDR clients are US-based
 *
 * Usage:
 * ./scripts/generate-test-data.sh
 */
@SpringBootTest
@Transactional
public class TestDataRunner {

    private static final Logger log = LoggerFactory.getLogger(TestDataRunner.class);

    private final Faker canadianFaker = new Faker(new Locale("en-CA"));
    private final Faker usFaker = new Faker(new Locale("en-US"));
    private final Random random = new Random();

    // US States with abbreviations
    private static final String[] US_STATES = {
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
        "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
        "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
        "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
        "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY"
    };

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientAccountRepository clientAccountRepository;

    @Value("${test.data.client-count:10000}")
    private int clientCount;

    @Test
    @Commit
    @Disabled("Run manually via script")
    void generateTestData() {
        log.info("=================================================================");
        log.info("Starting Test Data Generation (using Domain Layer)");
        log.info("=================================================================");
        log.info("Configuration:");
        log.info("  - Client Count: {}", clientCount);
        log.info("  - Account Distribution: Skewed (3-10 common, 5% have 100-1000)");
        log.info("=================================================================");

        long startTime = System.currentTimeMillis();

        // Calculate split: 60% SRF (Canadian), 40% CDR (US)
        int srfCount = (int) (clientCount * 0.6);
        int cdrCount = clientCount - srfCount;

        // Generate SRF clients (Canadian, 9-digit IDs)
        log.info("Generating {} SRF clients...", srfCount);
        List<Client> srfClients = generateClients(srfCount, "srf");

        // Generate CDR clients (US, 6-digit IDs)
        log.info("Generating {} CDR clients...", cdrCount);
        List<Client> cdrClients = generateClients(cdrCount, "cdr");

        // Generate accounts for all clients with skewed distribution
        int totalAccounts = 0;
        log.info("Generating accounts for clients (skewed distribution)...");

        List<Client> allClients = new ArrayList<>();
        allClients.addAll(srfClients);
        allClients.addAll(cdrClients);

        int clientsWithManyAccounts = 0;
        for (int i = 0; i < allClients.size(); i++) {
            Client client = allClients.get(i);
            int accountCount = generateSkewedAccountCount();
            if (accountCount > 100) {
                clientsWithManyAccounts++;
            }
            List<ClientAccount> accounts = generateAccounts(client.clientId(), accountCount);
            totalAccounts += accounts.size();

            if ((i + 1) % 100 == 0) {
                log.info("Generated accounts for {} clients ({} accounts total)", i + 1, totalAccounts);
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("=================================================================");
        log.info("Test Data Generation Complete");
        log.info("=================================================================");
        log.info("Summary:");
        log.info("  - Total Clients: {}", clientCount);
        log.info("    - SRF Clients (Canadian): {}", srfCount);
        log.info("    - CDR Clients (US): {}", cdrCount);
        log.info("  - Total Accounts: {}", totalAccounts);
        log.info("  - Clients with >100 accounts: {} ({:.1f}%)",
                clientsWithManyAccounts, (clientsWithManyAccounts * 100.0 / clientCount));
        log.info("  - Duration: {} ms ({} seconds)", duration, duration / 1000.0);
        log.info("=================================================================");

        // Log sample data
        if (!srfClients.isEmpty()) {
            Client sample = srfClients.get(0);
            log.info("Sample SRF Client (Canadian):");
            log.info("  - ID: {}", sample.clientId().urn());
            log.info("  - Name: {}", sample.name());
            log.info("  - Type: {}", sample.clientType());
            log.info("  - Address: {}, {} {} ({})", sample.address().city(),
                    sample.address().stateProvince(), sample.address().zipPostalCode(),
                    sample.address().countryCode());
        }

        if (!cdrClients.isEmpty()) {
            Client sample = cdrClients.get(0);
            log.info("Sample CDR Client (US):");
            log.info("  - ID: {}", sample.clientId().urn());
            log.info("  - Name: {}", sample.name());
            log.info("  - Type: {}", sample.clientType());
            log.info("  - Address: {}, {} {} ({})", sample.address().city(),
                    sample.address().stateProvince(), sample.address().zipPostalCode(),
                    sample.address().countryCode());
        }

        log.info("=================================================================");
    }

    /**
     * Generates a skewed account count:
     * - 95% of clients: 3-10 accounts (heavily weighted towards lower end)
     * - 5% of clients: 100-1000 accounts
     */
    private int generateSkewedAccountCount() {
        double roll = random.nextDouble();

        if (roll < 0.95) {
            // 95% of clients have 3-10 accounts
            // Use a distribution skewed towards lower numbers
            double skewedRoll = Math.pow(random.nextDouble(), 2); // Squares to skew towards 0
            return 3 + (int) (skewedRoll * 8); // 3 to 10
        } else {
            // 5% of clients have 100-1000 accounts
            // Also skewed towards lower end of range
            double skewedRoll = Math.pow(random.nextDouble(), 1.5);
            return 100 + (int) (skewedRoll * 900); // 100 to 1000
        }
    }

    private List<Client> generateClients(int count, String idPrefix) {
        List<Client> clients = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Client client = createClient(idPrefix);
            clientRepository.save(client);
            clients.add(client);

            if ((i + 1) % 10 == 0) {
                log.info("Generated {} {} clients", i + 1, idPrefix.toUpperCase());
            }
        }
        return clients;
    }

    private Client createClient(String idPrefix) {
        boolean isUS = "cdr".equals(idPrefix);

        // Generate client ID using proper value objects
        // SRF clients have 9-digit IDs, CDR clients have 6-digit IDs
        ClientId clientId;
        if ("srf".equals(idPrefix)) {
            String number = String.format("%09d", random.nextInt(1000000000));
            clientId = new SrfClientId(number);
        } else {
            String number = String.format("%06d", random.nextInt(1000000));
            clientId = new CdrClientId(number);
        }

        // Business name (US or Canadian style)
        String name = generateBusinessName(isUS);

        // Client type - mostly BUSINESS
        ClientType clientType = random.nextDouble() > 0.2
            ? ClientType.BUSINESS
            : ClientType.INDIVIDUAL;

        // Address based on ID prefix:
        // SRF clients are always Canadian, CDR clients are always US
        Address address = isUS ? createUSAddress() : createCanadianAddress();

        // Create client using domain factory method
        return Client.create(clientId, name, clientType, address);
    }

    private String generateBusinessName(boolean isUS) {
        Faker faker = isUS ? usFaker : canadianFaker;
        String[] suffixes = isUS
            ? new String[]{"Inc.", "Corp.", "LLC", "Co.", "Group", "Enterprises", "Holdings"}
            : new String[]{"Inc.", "Ltd.", "Corp.", "Co.", "Group", "Enterprises"};

        if (random.nextBoolean()) {
            return faker.name().lastName() + " " + suffixes[random.nextInt(suffixes.length)];
        } else {
            return faker.commerce().productName().split(" ")[0] + " " +
                   faker.commerce().department() + " " + suffixes[random.nextInt(suffixes.length)];
        }
    }

    private Address createCanadianAddress() {
        String[] provinces = {"ON", "QC", "BC", "AB", "MB", "SK", "NS", "NB"};
        return Address.of(
            canadianFaker.address().streetAddress(),
            random.nextDouble() > 0.7 ? "Suite " + canadianFaker.number().digits(3) : null,
            canadianFaker.address().city(),
            provinces[random.nextInt(provinces.length)],
            generateCanadianPostalCode(),
            "CA"
        );
    }

    private Address createUSAddress() {
        return Address.of(
            usFaker.address().streetAddress(),
            random.nextDouble() > 0.7 ? "Suite " + usFaker.number().digits(3) : null,
            usFaker.address().city(),
            US_STATES[random.nextInt(US_STATES.length)],
            generateUSZipCode(),
            "US"
        );
    }

    /**
     * Generates a 5-digit US ZIP code
     */
    private String generateUSZipCode() {
        return String.format("%05d", random.nextInt(100000));
    }

    private String generateCanadianPostalCode() {
        char[] validFirstLetters = "ABCEGHJKLMNPRSTVXY".toCharArray();
        char[] validLetters = "ABCEGHJKLMNPRSTVWXYZ".toCharArray();
        return String.valueOf(validFirstLetters[random.nextInt(validFirstLetters.length)]) +
               random.nextInt(10) +
               validLetters[random.nextInt(validLetters.length)] + " " +
               random.nextInt(10) +
               validLetters[random.nextInt(validLetters.length)] +
               random.nextInt(10);
    }

    private List<ClientAccount> generateAccounts(ClientId clientId, int count) {
        List<ClientAccount> accounts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ClientAccount account = createAccount(clientId);
            clientAccountRepository.save(account);
            accounts.add(account);
        }
        return accounts;
    }

    private ClientAccount createAccount(ClientId clientId) {
        // Determine if SRF or CDR based on client ID
        boolean isSrfClient = clientId.urn().startsWith("srf:");

        // Generate account using proper value objects
        // SRF clients use Canadian account systems, CDR clients use US systems
        AccountSystem[] canadianSystems = {AccountSystem.CAN_DDA, AccountSystem.CAN_FCA};
        AccountSystem[] usSystems = {AccountSystem.US_FIN, AccountSystem.US_FIS};

        ClientAccountId accountId;
        Currency currency;

        if (isSrfClient) {
            // Canadian account for SRF clients
            AccountSystem system = canadianSystems[random.nextInt(canadianSystems.length)];
            String accountType = AccountType.DDA.name();
            String transit = String.format("%05d", random.nextInt(100000));
            String accountNumber = String.format("%012d", random.nextLong(1000000000000L));
            accountId = new ClientAccountId(system, accountType, transit + ":" + accountNumber);
            currency = Currency.of("CAD");
        } else {
            // US account for CDR clients
            AccountSystem system = usSystems[random.nextInt(usSystems.length)];
            AccountType[] types = {AccountType.DDA, AccountType.CC, AccountType.LOC};
            String accountType = types[random.nextInt(types.length)].name();
            String accountNumber = String.format("%010d", random.nextLong(10000000000L));
            accountId = new ClientAccountId(system, accountType, accountNumber);
            currency = Currency.of("USD");
        }

        // Create account using domain factory method
        return ClientAccount.create(accountId, clientId, currency);
    }
}
