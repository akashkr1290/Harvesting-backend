package com.harvestflow.api.config;

import com.harvestflow.api.domain.entity.*;
import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.domain.enums.ListingStatus;
import com.harvestflow.api.domain.enums.MasterCategory;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.repository.DeliveryVehicleRepository;
import com.harvestflow.api.repository.HarvestCaseRepository;
import com.harvestflow.api.repository.MasterItemRepository;
import com.harvestflow.api.repository.ProduceListingRepository;
import com.harvestflow.api.repository.UserRepository;
import com.harvestflow.api.util.CredentialGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Seeds enough data for the API to be usable immediately against the
 * Flutter app, mirroring the two sample cases the frontend's mock
 * CaseService seeds — one at the very first stage, one already through
 * Planning + Rate Update — so every stage screen has something to
 * demo without manually walking a case through the whole pipeline first.
 *
 * Phase 5: merges what were three independently-diverged seeders (Phase 2's
 * marketplace demo accounts/listings, Phase 3's geocoded locations/companies
 * + demand-history + vehicles, Phase 1's base internal-ops data) into one.
 *
 * Disable with harvestflow.seed.enabled=false (already off in the test profile).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MasterItemRepository masterItemRepository;
    private final HarvestCaseRepository caseRepository;
    private final ProduceListingRepository listingRepository;
    private final DeliveryVehicleRepository deliveryVehicleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CredentialGenerator credentialGenerator;

    @Value("${harvestflow.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedEnabled || userRepository.count() > 0) {
            return;
        }

        log.info("Seeding HarvestFlow demo data...");
        seedUsers();
        seedMasterData();
        seedCases();
        seedMarketplace();
        seedDemandHistory();
        seedVehicles();
        log.info("Seed complete.");
    }

    private void seedUsers() {
        createUser("Admin User", "9000000000", "admin@harvestflow.com", UserRole.ADMIN, "Head Office", "admin123");
        createUser("Priya Sharma", "9811122233", "priya@harvestflow.com", UserRole.PLOT_SELECTION, "Nashik", "plot123");
        createUser("Rahul Deshpande", "9811100011", "rahul@harvestflow.com", UserRole.PLANNING, "Nashik", "plan123");
        createUser("Suman Gaikwad", "9811100022", "suman@harvestflow.com", UserRole.GODOWN, "Nashik", "god123");
        createUser("Ganesh More", "9822033344", "ganesh@harvestflow.com", UserRole.SUPERVISOR, "Kolhapur", "sup123");
        createUser("Vikram Joshi", "9811100033", "vikram@harvestflow.com", UserRole.PURCHASE_ACCOUNT, "Nashik", "pur123");
        createUser("Ramesh Sable", "9811100044", "ramesh@harvestflow.com", UserRole.PICKUP_PERSON, "Kolhapur", "pick123");
        createUser("Anita Kulkarni", "9811100055", "anita@harvestflow.com", UserRole.TRANSPORT_PERSON, "Nashik", "trans123");
        createUser("Suresh Patil", "9811100066", "sureshp@harvestflow.com", UserRole.LABOR_COORDINATOR, "Nashik", "labor123");
        createUser("Dilip Chavan", "9811100077", "dilip@harvestflow.com", UserRole.EICHER_DRIVER, "Nashik", "drive123");

        // ---- Phase 2: Marketplace demo accounts ----
        createUser("Sunil Wagh", "9900011122", "sunil.farmer@harvestflow.com", UserRole.FARMER, "Nashik", "farmer123");
        createUser("Chandori Farmers FPO", "9900011133", "chandori.fpo@harvestflow.com", UserRole.FPO, "Nashik", "fpo123");
        createUser("Meera Kulkarni", "9900011144", "meera.consumer@harvestflow.com", UserRole.CONSUMER, "Pune", "buyer123");
        createUser("FreshMart Wholesale", "9900011155", "freshmart.bulk@harvestflow.com", UserRole.BULK_BUYER, "Mumbai", "bulk123");
    }

    /**
     * One published listing (ready for a buyer to make an offer on) and one
     * draft (ready for the farmer demo account to publish/edit), so both
     * sides of Phase 2's marketplace UI have something to work with
     * immediately.
     */
    private void seedMarketplace() {
        User farmer = userRepository.findByUsername("sunil.wagh").orElseThrow();
        
        ProduceListing published = new ProduceListing();
        published.setSeller(farmer);
        published.setSellerRole(farmer.getRole());
        published.setSellerName(farmer.getName());
        published.setCropName("Sugarcane");
        published.setQuantity(2000.0);
        published.setUnit("kg");
        published.setQuality("Grade A");
        published.setHarvestDate(LocalDate.now().plusDays(3));
        published.setLocation("Nashik");
        published.setExpectedPricePerUnit(3.2);
        published.setDescription("Fresh sugarcane, harvested from a 2-acre plot near Chandori.");
        published.setStatus(ListingStatus.PUBLISHED);
        published.setPublishedAt(java.time.Instant.now());
        listingRepository.save(published);

        ProduceListing draft = new ProduceListing();
        draft.setSeller(farmer);
        draft.setSellerRole(farmer.getRole());
        draft.setSellerName(farmer.getName());
        draft.setCropName("Onion");
        draft.setQuantity(500.0);
        draft.setUnit("kg");
        draft.setQuality("Grade B");
        draft.setHarvestDate(LocalDate.now().plusDays(7));
        draft.setLocation("Nashik");
        draft.setExpectedPricePerUnit(18.0);
        draft.setDescription("Not yet published — still finalizing quantity.");
        listingRepository.save(draft);
    }

    private void createUser(String name, String mobile, String email, UserRole role, String location, String knownPassword) {
        User user = new User();
        user.setName(name);
        user.setMobile(mobile);
        user.setEmail(email);
        user.setRole(role);
        user.setLocation(location);
        user.setUsername(credentialGenerator.generateUsername(name));
        // Seeded users get a known, documented password (see README) rather
        // than a random temp password, purely so the demo is usable without
        // digging through logs — real user creation always goes through
        // UserService's auto-generated temp password + forced change flow.
        user.setPasswordHash(passwordEncoder.encode(knownPassword));
        user.setMustChangePassword(false);
        user.setActive(true);
        userRepository.save(user);
    }

    private void seedMasterData() {
        seedLocation("Nashik", 19.9975, 73.7898);
        seedLocation("Kolhapur", 16.7050, 74.2433);
        seedLocation("Sangli", 16.8524, 74.5815);
        seedCategory(MasterCategory.VILLAGES, "Chandori", "Hupari");
        seedCategory(MasterCategory.COMMISSION_AGENTS, "R. Patil", "V. Deshmukh");
        seedCategory(MasterCategory.FARMERS, "Suresh Jadhav", "Anil Kadam");
        seedCompany("AgroFresh Pvt Ltd", 19.2183, 72.9781); // Thane collection center, used as delivery destination in the route demo
        seedCompany("SweetCane Exports", 18.5204, 73.8567); // Pune
        seedCategory(MasterCategory.CROP_TYPES, "Sugarcane", "Mango", "Onion");
        seedCategory(MasterCategory.VEHICLE_TYPES, "Eicher Truck", "Tractor Trolley");
        seedCategory(MasterCategory.PACKING_ITEMS, "Jute Bags", "Plastic Crates", "Nylon Rope");
        seedCategory(MasterCategory.UNITS_OF_MEASURE, "kg", "ton", "pcs");
    }

    private void seedCategory(MasterCategory category, String... names) {
        for (String name : names) {
            masterItemRepository.save(new MasterItem(category, name));
        }
    }

    /** LOCATIONS entries get real coordinates so the route optimization demo can resolve pickup/delivery stops by name alone. */
    private void seedLocation(String name, double lat, double lng) {
        MasterItem item = new MasterItem(MasterCategory.LOCATIONS, name);
        item.setLatitude(lat);
        item.setLongitude(lng);
        masterItemRepository.save(item);
    }

    private void seedCompany(String name, double lat, double lng) {
        MasterItem item = new MasterItem(MasterCategory.COMPANIES, name);
        item.setLatitude(lat);
        item.setLongitude(lng);
        masterItemRepository.save(item);
    }

    private void seedCases() {
        HarvestCase first = new HarvestCase();
        first.setLocation("Nashik");
        first.setVisitDate(LocalDate.now().minusDays(1));
        first.setCommissionAgentName("R. Patil");
        first.setFarmerName("Suresh Jadhav");
        first.setFarmerPhone("9876543210");
        first.setVillage("Chandori");
        first.setNumberOfPlants(420);
        first.setHarvestingDate(LocalDate.now().plusDays(1));
        first.setVolume(1200.0);
        first.setRecovery(11.5);
        first.setPulp(46.2);
        first.setSelectedCompany("AgroFresh Pvt Ltd");
        first.setCropType("Sugarcane");
        first.setPlotCode("NSK-014");
        first.setPriority("high");
        first.setCreatedByUsername("priya.sharma");
        first.addTimelineEntry("priya.sharma", "Created plot selection record");
        caseRepository.save(first);

        HarvestCase second = new HarvestCase();
        second.setLocation("Kolhapur");
        second.setVisitDate(LocalDate.now().minusDays(3));
        second.setCommissionAgentName("V. Deshmukh");
        second.setFarmerName("Anil Kadam");
        second.setFarmerPhone("9822011223");
        second.setVillage("Hupari");
        second.setNumberOfPlants(300);
        second.setHarvestingDate(LocalDate.now());
        second.setVolume(950.0);
        second.setRecovery(10.8);
        second.setPulp(44.0);
        second.setSelectedCompany("SweetCane Exports");
        second.setCropType("Sugarcane");
        second.setPlotCode("KOP-007");
        second.setPriority("medium");
        second.setCreatedByUsername("priya.sharma");
        second.addTimelineEntry("priya.sharma", "Created plot selection record");

        PlanningDetails planning = new PlanningDetails();
        planning.setHarvestCase(second);
        planning.setPlannedDate(LocalDate.now());
        planning.setTimeWindow("Morning (6 AM – 10 AM)");
        planning.setSupervisorName("Ganesh More");
        planning.setPickupPersonName("Ramesh Sable");
        planning.setExpectedQuantity(950.0);
        second.setPlanning(planning);
        second.setStatus(CaseStatus.PLANNED);
        second.addTimelineEntry("rahul.deshpande", "Created harvest plan — supervisor Ganesh More");

        second.setRatePerUnit(32.5);
        second.setStatus(CaseStatus.RATE_UPDATED);
        second.addTimelineEntry("vikram.joshi", "Updated purchase rate to 32.5/unit");

        caseRepository.save(second);
    }

    /**
     * A handful of already-completed cases across several past months, so
     * demand forecasting (Phase 3) has something to compute from out of
     * the box instead of immediately hitting "insufficient data" in a
     * fresh demo environment. Clearly demo/test data, not real market
     * history — see the class Javadoc.
     */
    private void seedDemandHistory() {
        double[] monthlyWeights = {1100.0, 1250.0, 1180.0, 1400.0};
        for (int i = 0; i < monthlyWeights.length; i++) {
            HarvestCase demoCase = new HarvestCase();
            demoCase.setLocation("Nashik");
            demoCase.setVisitDate(LocalDate.now().minusMonths(monthlyWeights.length - i).minusDays(20));
            demoCase.setCommissionAgentName("R. Patil");
            demoCase.setFarmerName("Suresh Jadhav");
            demoCase.setFarmerPhone("9876543210");
            demoCase.setVillage("Chandori");
            demoCase.setNumberOfPlants(400);
            demoCase.setHarvestingDate(LocalDate.now().minusMonths(monthlyWeights.length - i).minusDays(10));
            demoCase.setVolume(monthlyWeights[i]);
            demoCase.setRecovery(11.0);
            demoCase.setPulp(45.0);
            demoCase.setSelectedCompany("AgroFresh Pvt Ltd");
            demoCase.setCropType("Sugarcane");
            demoCase.setPlotCode("NSK-DEMO-" + i);
            demoCase.setPriority("medium");
            demoCase.setCreatedByUsername("priya.sharma");
            demoCase.setStatus(CaseStatus.HARVESTING_COMPLETED);
            demoCase.addTimelineEntry("priya.sharma", "Created plot selection record");

            HarvestCompletion completion = new HarvestCompletion();
            completion.setHarvestCase(demoCase);
            completion.setActualDate(LocalDate.now().minusMonths(monthlyWeights.length - i).minusDays(9));
            completion.setActualWeight(monthlyWeights[i]);
            completion.setFinalVolume(monthlyWeights[i]);
            completion.setFinalRecovery(11.0);
            completion.setFinalPulp(45.0);
            demoCase.setCompletion(completion);
            demoCase.addTimelineEntry("ganesh.more", "Recorded harvest completion (demo data)");

            caseRepository.save(demoCase);
        }
    }

    private void seedVehicles() {
        DeliveryVehicle truck = new DeliveryVehicle();
        truck.setRegistrationNumber("MH-15-AB-1234");
        truck.setVehicleType("Eicher Truck");
        truck.setCapacityKg(5000.0);
        deliveryVehicleRepository.save(truck);

        DeliveryVehicle tractor = new DeliveryVehicle();
        tractor.setRegistrationNumber("MH-09-CD-5678");
        tractor.setVehicleType("Tractor Trolley");
        tractor.setCapacityKg(1500.0);
        deliveryVehicleRepository.save(tractor);
    }
}
