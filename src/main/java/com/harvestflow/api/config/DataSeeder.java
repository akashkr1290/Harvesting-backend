package com.harvestflow.api.config;

import com.harvestflow.api.domain.entity.*;
import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.domain.enums.MasterCategory;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.repository.HarvestCaseRepository;
import com.harvestflow.api.repository.MasterItemRepository;
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
 * Disable with harvestflow.seed.enabled=false (already off in the test profile).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MasterItemRepository masterItemRepository;
    private final HarvestCaseRepository caseRepository;
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
        seedCategory(MasterCategory.LOCATIONS, "Nashik", "Kolhapur", "Sangli");
        seedCategory(MasterCategory.VILLAGES, "Chandori", "Hupari");
        seedCategory(MasterCategory.COMMISSION_AGENTS, "R. Patil", "V. Deshmukh");
        seedCategory(MasterCategory.FARMERS, "Suresh Jadhav", "Anil Kadam");
        seedCategory(MasterCategory.COMPANIES, "AgroFresh Pvt Ltd", "SweetCane Exports");
        seedCategory(MasterCategory.CROP_TYPES, "Sugarcane");
        seedCategory(MasterCategory.VEHICLE_TYPES, "Eicher Truck", "Tractor Trolley");
        seedCategory(MasterCategory.PACKING_ITEMS, "Jute Bags", "Plastic Crates", "Nylon Rope");
        seedCategory(MasterCategory.UNITS_OF_MEASURE, "kg", "ton", "pcs");
    }

    private void seedCategory(MasterCategory category, String... names) {
        for (String name : names) {
            masterItemRepository.save(new MasterItem(category, name));
        }
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
}
