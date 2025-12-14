package com.knight.application.testdata;

import com.github.javafaker.Faker;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.platform.sharedkernel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Test data generator using JavaFaker to create realistic client and account test data.
 * Generates Canadian/US business names, addresses, and properly formatted account numbers.
 */
@Component
public class TestDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(TestDataGenerator.class);

    private final ClientRepository clientRepository;
    private final ClientAccountRepository accountRepository;
    private final Faker faker;
    private final Random random;

    public TestDataGenerator(ClientRepository clientRepository, ClientAccountRepository accountRepository) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.faker = new Faker(new Locale("en-CA"));
        this.random = new Random();
    }

    /**
     * Generates and persists a specified number of clients.
     *
     * @param count the number of clients to generate
     * @param clientType "SRF" or "CDR"
     * @return list of generated clients
     */
    public List<Client> generateClients(int count, String clientType) {
        List<Client> clients = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Client client = generateClient(clientType);
            clientRepository.save(client);
            clients.add(client);

            if ((i + 1) % 10 == 0) {
                log.info("Generated {} {} clients", i + 1, clientType);
            }
        }

        log.info("Successfully generated {} {} clients", count, clientType);
        return clients;
    }

    /**
     * Generates and persists accounts for a specific client.
     *
     * @param clientId the client to generate accounts for
     * @param count the number of accounts to generate
     * @return list of generated accounts
     */
    public List<ClientAccount> generateAccountsForClient(ClientId clientId, int count) {
        List<ClientAccount> accounts = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            ClientAccount account = generateAccount(clientId);
            accountRepository.save(account);
            accounts.add(account);
        }

        return accounts;
    }

    /**
     * Generates a single client with realistic data.
     */
    private Client generateClient(String clientType) {
        // Generate client ID based on type
        ClientId clientId = generateClientId(clientType);

        // Generate business name
        String name = generateBusinessName();

        // Generate Canadian or US address
        Address address = generateAddress();

        // Create client
        Client client = Client.create(clientId, name, Client.ClientType.BUSINESS, address);

        // Add optional contact information
        String phone = generatePhoneNumber();
        String email = generateBusinessEmail(name);
        client.updateContactInfo(phone, email);

        // Add tax ID
        String taxId = generateTaxId();
        client.updateTaxId(taxId);

        return client;
    }

    /**
     * Generates a client ID based on the specified type.
     */
    private ClientId generateClientId(String clientType) {
        // Generate 6-digit client number
        String clientNumber = String.format("%06d", random.nextInt(1000000));

        return switch (clientType.toUpperCase()) {
            case "SRF" -> new SrfClientId(clientNumber);
            case "CDR" -> new CdrClientId(clientNumber);
            default -> throw new IllegalArgumentException("Invalid client type: " + clientType);
        };
    }

    /**
     * Generates a realistic Canadian business name.
     */
    private String generateBusinessName() {
        String[] suffixes = {"Inc.", "Ltd.", "Corp.", "Co.", "Group", "Enterprises", "Industries", "Solutions"};

        // Mix of company name patterns
        if (random.nextBoolean()) {
            // Pattern: [Last Name] + [Suffix]
            return faker.name().lastName() + " " + suffixes[random.nextInt(suffixes.length)];
        } else {
            // Pattern: [Adjective] + [Industry] + [Suffix]
            String adjective = faker.commerce().productName().split(" ")[0];
            String industry = faker.commerce().department();
            return adjective + " " + industry + " " + suffixes[random.nextInt(suffixes.length)];
        }
    }

    /**
     * Generates a Canadian or US address.
     */
    private Address generateAddress() {
        boolean isCanadian = random.nextDouble() > 0.3; // 70% Canadian, 30% US

        if (isCanadian) {
            return generateCanadianAddress();
        } else {
            return generateUSAddress();
        }
    }

    /**
     * Generates a Canadian address.
     */
    private Address generateCanadianAddress() {
        String[] provinces = {"ON", "QC", "BC", "AB", "MB", "SK", "NS", "NB", "NL", "PE"};

        String addressLine1 = faker.address().streetAddress();
        String addressLine2 = random.nextDouble() > 0.7 ? "Suite " + faker.number().digits(3) : null;
        String city = faker.address().city();
        String province = provinces[random.nextInt(provinces.length)];
        String postalCode = generateCanadianPostalCode();

        return new Address(addressLine1, addressLine2, city, province, postalCode, "CA");
    }

    /**
     * Generates a US address.
     */
    private Address generateUSAddress() {
        String addressLine1 = faker.address().streetAddress();
        String addressLine2 = random.nextDouble() > 0.7 ? "Suite " + faker.number().digits(3) : null;
        String city = faker.address().city();
        String state = faker.address().stateAbbr();
        String zipCode = faker.address().zipCode();

        return new Address(addressLine1, addressLine2, city, state, zipCode, "US");
    }

    /**
     * Generates a valid Canadian postal code (A1A 1A1 format).
     */
    private String generateCanadianPostalCode() {
        char[] validFirstLetters = "ABCEGHJKLMNPRSTVXY".toCharArray();
        char[] validLetters = "ABCEGHJKLMNPRSTVWXYZ".toCharArray();

        StringBuilder postalCode = new StringBuilder();
        postalCode.append(validFirstLetters[random.nextInt(validFirstLetters.length)]);
        postalCode.append(random.nextInt(10));
        postalCode.append(validLetters[random.nextInt(validLetters.length)]);
        postalCode.append(' ');
        postalCode.append(random.nextInt(10));
        postalCode.append(validLetters[random.nextInt(validLetters.length)]);
        postalCode.append(random.nextInt(10));

        return postalCode.toString();
    }

    /**
     * Generates a North American phone number.
     */
    private String generatePhoneNumber() {
        return String.format("+1-%03d-%03d-%04d",
                random.nextInt(900) + 100,
                random.nextInt(900) + 100,
                random.nextInt(10000));
    }

    /**
     * Generates a business email based on the company name.
     */
    private String generateBusinessEmail(String companyName) {
        String domain = companyName
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .substring(0, Math.min(companyName.length(), 15));

        String[] contactTypes = {"info", "contact", "admin", "business"};
        return contactTypes[random.nextInt(contactTypes.length)] + "@" + domain + ".com";
    }

    /**
     * Generates a tax ID (Canadian BN or US EIN format).
     */
    private String generateTaxId() {
        if (random.nextBoolean()) {
            // Canadian Business Number: 9 digits
            return String.format("%09d", random.nextInt(1000000000));
        } else {
            // US EIN: XX-XXXXXXX
            return String.format("%02d-%07d",
                    random.nextInt(100),
                    random.nextInt(10000000));
        }
    }

    /**
     * Generates a client account with realistic account numbers.
     */
    private ClientAccount generateAccount(ClientId clientId) {
        ClientAccountId accountId = generateAccountId();
        Currency currency = generateCurrency();

        return ClientAccount.create(accountId, clientId, currency);
    }

    /**
     * Generates a random account ID with proper format based on account system.
     */
    private ClientAccountId generateAccountId() {
        AccountSystem[] canadianSystems = {
                AccountSystem.CAN_DDA,
                AccountSystem.CAN_FCA,
                AccountSystem.CAN_LOC,
                AccountSystem.CAN_MTG
        };
        AccountSystem[] usSystems = {AccountSystem.US_FIN, AccountSystem.US_FIS};

        // 70% Canadian, 20% US, 10% OFI
        double systemChoice = random.nextDouble();

        if (systemChoice < 0.7) {
            // Canadian account
            return generateCanadianAccount(canadianSystems[random.nextInt(canadianSystems.length)]);
        } else if (systemChoice < 0.9) {
            // US account
            return generateUSAccount(usSystems[random.nextInt(usSystems.length)]);
        } else {
            // OFI account
            return generateOFIAccount();
        }
    }

    /**
     * Generates a Canadian account ID (transit:accountNumber format).
     */
    private ClientAccountId generateCanadianAccount(AccountSystem system) {
        String transit = String.format("%05d", random.nextInt(100000));
        String accountNumber = String.format("%012d", random.nextLong(1000000000000L));
        String segments = transit + ":" + accountNumber;

        String accountType = switch (system) {
            case CAN_DDA -> "DDA";
            case CAN_FCA -> "DDA"; // FCA typically uses DDA type
            case CAN_LOC -> "LOC";
            case CAN_MTG -> "MTG";
            default -> "DDA";
        };

        return new ClientAccountId(system, accountType, segments);
    }

    /**
     * Generates a US account ID.
     */
    private ClientAccountId generateUSAccount(AccountSystem system) {
        String accountNumber = String.format("%010d", random.nextLong(10000000000L));

        AccountType[] types = {AccountType.DDA, AccountType.CC, AccountType.LOC, AccountType.MTG};
        String accountType = types[random.nextInt(types.length)].name();

        return new ClientAccountId(system, accountType, accountNumber);
    }

    /**
     * Generates an OFI (Other Financial Institution) account ID.
     */
    private ClientAccountId generateOFIAccount() {
        String[] ofiTypes = {"CAN", "US"};
        String ofiType = ofiTypes[random.nextInt(ofiTypes.length)];

        String segments = switch (ofiType) {
            case "CAN" -> {
                // Format: bank(3):transit(5):accountNumber(12)
                String bank = String.format("%03d", random.nextInt(1000));
                String transit = String.format("%05d", random.nextInt(100000));
                String accountNumber = String.format("%012d", random.nextLong(1000000000000L));
                yield bank + ":" + transit + ":" + accountNumber;
            }
            case "US" -> {
                // Format: abaRouting(9):accountNumber(up to 17)
                String routing = String.format("%09d", random.nextInt(1000000000));
                String accountNumber = String.format("%012d", random.nextLong(1000000000000L));
                yield routing + ":" + accountNumber;
            }
            default -> throw new IllegalStateException("Unexpected OFI type: " + ofiType);
        };

        return new ClientAccountId(AccountSystem.OFI, ofiType, segments);
    }

    /**
     * Generates a currency (CAD or USD).
     */
    private Currency generateCurrency() {
        return random.nextDouble() > 0.3 ? Currency.CAD : Currency.USD;
    }
}
