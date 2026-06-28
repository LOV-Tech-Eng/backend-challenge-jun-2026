package com.cloudcart.disputes.infra;

import com.cloudcart.disputes.api.dto.ProcessorARequest;
import com.cloudcart.disputes.api.dto.ProcessorBRequest;
import com.cloudcart.disputes.api.mapper.ProcessorNormalizer;
import com.cloudcart.disputes.domain.model.Dispute;
import com.cloudcart.disputes.service.DisputeIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Seeds the in-memory database with 150+ disputes at startup.
 * Runs through DisputeIngestionService so scoring is computed by the engine
 * (same pipeline as the real API), not hardcoded in SQL.
 *
 * Distribution:
 *   - 5 merchants: MERCHANT_001 through MERCHANT_005
 *   - 4 currencies: USD, BRL, MXN, COP
 *   - 5 reason categories (weighted toward FRAUD to reflect the "chargeback storm")
 *   - Deadlines: ~20% expired, ~15% urgent (1–3 days), ~25% medium (4–10 days), ~40% comfortable (11+ days)
 *   - Amounts: $15 to $3500
 *   - Both processor formats used
 */
@Component
@Profile("!test")
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final ProcessorNormalizer normalizer;
    private final DisputeIngestionService ingestionService;

    // Reference date for computing relative deadlines
    private static final LocalDate TODAY = LocalDate.now();

    public DataLoader(ProcessorNormalizer normalizer, DisputeIngestionService ingestionService) {
        this.normalizer = normalizer;
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(String... args) {
        AtomicInteger count = new AtomicInteger(0);

        buildProcessorADisputes().forEach(req -> {
            Dispute d = normalizer.fromProcessorA(req);
            ingestionService.ingest(d);
            count.incrementAndGet();
        });

        buildProcessorBDisputes().forEach(req -> {
            Dispute d = normalizer.fromProcessorB(req);
            ingestionService.ingest(d);
            count.incrementAndGet();
        });

        log.info("DataLoader: seeded {} disputes across 5 merchants (2 processor formats)", count.get());
    }

    // ---- Processor A records ----

    private List<ProcessorARequest> buildProcessorADisputes() {
        List<ProcessorARequest> records = new ArrayList<>();

        // MERCHANT_001 (Brazil, BRL) — mix of FRAUD heavy (40 records)
        String[] fraudCodes   = {"10.4", "4837", "10.1", "4863"};
        String[] productCodes = {"13.1", "4855", "13.3", "4853"};
        String[] subCodes     = {"13.2", "4841"};
        String[] dupCodes     = {"12.6", "4834"};

        // Expired disputes (deadline in the past)
        records.add(pa("PA-M1-001", "MERCHANT_001", "250.00", "BRL", "10.4", -30, -30));
        records.add(pa("PA-M1-002", "MERCHANT_001", "1200.00", "BRL", "4837", -15, -15));
        records.add(pa("PA-M1-003", "MERCHANT_001", "75.00",  "BRL", "13.1", -7,  -7));
        records.add(pa("PA-M1-004", "MERCHANT_001", "3500.00","BRL", "12.6", -3,  -3));
        records.add(pa("PA-M1-005", "MERCHANT_001", "450.00", "BRL", "4841", -1,  -1));

        // Urgent (1–3 days)
        records.add(pa("PA-M1-006", "MERCHANT_001", "890.00", "BRL", "10.4",  1,  1));
        records.add(pa("PA-M1-007", "MERCHANT_001", "320.00", "BRL", "13.3",  2,  2));
        records.add(pa("PA-M1-008", "MERCHANT_001", "2100.00","BRL", "4855",  3,  3));
        records.add(pa("PA-M1-009", "MERCHANT_001", "55.00",  "BRL", "4837",  1,  1));
        records.add(pa("PA-M1-010", "MERCHANT_001", "1750.00","BRL", "12.6",  2,  2));

        // Medium (4–10 days)
        records.add(pa("PA-M1-011", "MERCHANT_001", "620.00", "BRL", "10.4",  5,  5));
        records.add(pa("PA-M1-012", "MERCHANT_001", "180.00", "BRL", "13.1",  7,  7));
        records.add(pa("PA-M1-013", "MERCHANT_001", "940.00", "BRL", "4853",  9,  9));
        records.add(pa("PA-M1-014", "MERCHANT_001", "430.00", "BRL", "4841",  6,  6));
        records.add(pa("PA-M1-015", "MERCHANT_001", "88.00",  "BRL", "12.6",  8,  8));

        // Comfortable (11+ days)
        records.add(pa("PA-M1-016", "MERCHANT_001", "3000.00","BRL", "10.4", 14, 14));
        records.add(pa("PA-M1-017", "MERCHANT_001", "500.00", "BRL", "4837", 21, 21));
        records.add(pa("PA-M1-018", "MERCHANT_001", "1100.00","BRL", "13.1", 30, 30));
        records.add(pa("PA-M1-019", "MERCHANT_001", "275.00", "BRL", "13.3", 12, 12));
        records.add(pa("PA-M1-020", "MERCHANT_001", "680.00", "BRL", "4841", 18, 18));

        // MERCHANT_002 (Mexico, MXN) — product-heavy (30 records)
        records.add(pa("PA-M2-001", "MERCHANT_002", "1500.00","MXN", "13.1", -20, -20));
        records.add(pa("PA-M2-002", "MERCHANT_002", "200.00", "MXN", "4853", -5,  -5));
        records.add(pa("PA-M2-003", "MERCHANT_002", "3200.00","MXN", "10.4",  2,   2));
        records.add(pa("PA-M2-004", "MERCHANT_002", "780.00", "MXN", "13.3",  3,   3));
        records.add(pa("PA-M2-005", "MERCHANT_002", "95.00",  "MXN", "4855",  1,   1));
        records.add(pa("PA-M2-006", "MERCHANT_002", "420.00", "MXN", "12.6",  6,   6));
        records.add(pa("PA-M2-007", "MERCHANT_002", "2800.00","MXN", "4837",  9,   9));
        records.add(pa("PA-M2-008", "MERCHANT_002", "65.00",  "MXN", "13.1", 15,  15));
        records.add(pa("PA-M2-009", "MERCHANT_002", "1800.00","MXN", "4853", 20,  20));
        records.add(pa("PA-M2-010", "MERCHANT_002", "350.00", "MXN", "4841",  4,   4));
        records.add(pa("PA-M2-011", "MERCHANT_002", "125.00", "MXN", "10.4", -10, -10));
        records.add(pa("PA-M2-012", "MERCHANT_002", "900.00", "MXN", "13.3",  7,   7));
        records.add(pa("PA-M2-013", "MERCHANT_002", "3400.00","MXN", "12.6", 25,  25));
        records.add(pa("PA-M2-014", "MERCHANT_002", "47.00",  "MXN", "4855",  2,   2));
        records.add(pa("PA-M2-015", "MERCHANT_002", "1050.00","MXN", "4841", 11,  11));

        // MERCHANT_003 (Colombia, COP) — subscription and duplicate (25 records)
        records.add(pa("PA-M3-001", "MERCHANT_003", "85.00",  "COP", "13.2", -8,  -8));
        records.add(pa("PA-M3-002", "MERCHANT_003", "620.00", "COP", "4841",  1,   1));
        records.add(pa("PA-M3-003", "MERCHANT_003", "1900.00","COP", "12.6",  3,   3));
        records.add(pa("PA-M3-004", "MERCHANT_003", "310.00", "COP", "4834",  7,   7));
        records.add(pa("PA-M3-005", "MERCHANT_003", "2400.00","COP", "13.2", 12,  12));
        records.add(pa("PA-M3-006", "MERCHANT_003", "55.00",  "COP", "4841", -2,  -2));
        records.add(pa("PA-M3-007", "MERCHANT_003", "780.00", "COP", "12.6",  5,   5));
        records.add(pa("PA-M3-008", "MERCHANT_003", "150.00", "COP", "10.4",  9,   9));
        records.add(pa("PA-M3-009", "MERCHANT_003", "3100.00","COP", "4837", 20,  20));
        records.add(pa("PA-M3-010", "MERCHANT_003", "490.00", "COP", "13.3",  2,   2));

        // MERCHANT_004 (USD, mixed high-value) — 25 records
        records.add(pa("PA-M4-001", "MERCHANT_004", "3500.00","USD", "10.4", -14, -14));
        records.add(pa("PA-M4-002", "MERCHANT_004", "2750.00","USD", "4837",  1,   1));
        records.add(pa("PA-M4-003", "MERCHANT_004", "1800.00","USD", "13.1",  3,   3));
        records.add(pa("PA-M4-004", "MERCHANT_004", "980.00", "USD", "4853",  7,   7));
        records.add(pa("PA-M4-005", "MERCHANT_004", "450.00", "USD", "12.6", 14,  14));
        records.add(pa("PA-M4-006", "MERCHANT_004", "120.00", "USD", "4841", -3,  -3));
        records.add(pa("PA-M4-007", "MERCHANT_004", "2200.00","USD", "13.3",  2,   2));
        records.add(pa("PA-M4-008", "MERCHANT_004", "65.00",  "USD", "10.4",  5,   5));
        records.add(pa("PA-M4-009", "MERCHANT_004", "1450.00","USD", "4855", 21,  21));
        records.add(pa("PA-M4-010", "MERCHANT_004", "380.00", "USD", "4834",  9,   9));
        records.add(pa("PA-M4-011", "MERCHANT_004", "3200.00","USD", "10.4", 30,  30));
        records.add(pa("PA-M4-012", "MERCHANT_004", "875.00", "USD", "13.1",  6,   6));
        records.add(pa("PA-M4-013", "MERCHANT_004", "15.00",  "USD", "4841", -1,  -1));
        records.add(pa("PA-M4-014", "MERCHANT_004", "1750.00","USD", "12.6",  4,   4));
        records.add(pa("PA-M4-015", "MERCHANT_004", "600.00", "USD", "4853", 10,  10));

        return records;
    }

    // ---- Processor B records ----

    private List<ProcessorBRequest> buildProcessorBDisputes() {
        List<ProcessorBRequest> records = new ArrayList<>();

        // MERCHANT_005 (multi-currency, all categories) — 40+ records
        // Expired
        records.add(pb("PB-M5-001", "MERCHANT_005", 45000, "USD", "4837",  -25, -25));  // $450
        records.add(pb("PB-M5-002", "MERCHANT_005", 125000,"BRL", "10.4",  -12, -12));  // $1250
        records.add(pb("PB-M5-003", "MERCHANT_005",  8500, "MXN", "4855",   -6,  -6));  // $85
        records.add(pb("PB-M5-004", "MERCHANT_005", 320000,"COP", "13.2",   -2,  -2));  // $3200
        records.add(pb("PB-M5-005", "MERCHANT_005",  1500, "USD", "4834",   -1,  -1));  // $15

        // Urgent (1-3 days)
        records.add(pb("PB-M5-006", "MERCHANT_005", 180000,"BRL", "10.4",    1,   1));  // $1800
        records.add(pb("PB-M5-007", "MERCHANT_005",  6500, "MXN", "4853",    2,   2));  // $65
        records.add(pb("PB-M5-008", "MERCHANT_005", 275000,"COP", "12.6",    3,   3));  // $2750
        records.add(pb("PB-M5-009", "MERCHANT_005",  9900, "USD", "4841",    1,   1));  // $99
        records.add(pb("PB-M5-010", "MERCHANT_005", 350000,"BRL", "4855",    2,   2));  // $3500

        // Medium (4-10 days)
        records.add(pb("PB-M5-011", "MERCHANT_005",  55000,"USD", "10.4",    5,   5));  // $550
        records.add(pb("PB-M5-012", "MERCHANT_005",  25000,"MXN", "13.1",    7,   7));  // $250
        records.add(pb("PB-M5-013", "MERCHANT_005", 190000,"COP", "4853",    9,   9));  // $1900
        records.add(pb("PB-M5-014", "MERCHANT_005",  43000,"BRL", "4841",    6,   6));  // $430
        records.add(pb("PB-M5-015", "MERCHANT_005",   8800,"USD", "12.6",    8,   8));  // $88

        // Comfortable (11+ days)
        records.add(pb("PB-M5-016", "MERCHANT_005", 300000,"BRL", "10.4",   14,  14));  // $3000
        records.add(pb("PB-M5-017", "MERCHANT_005",  50000,"USD", "4837",   21,  21));  // $500
        records.add(pb("PB-M5-018", "MERCHANT_005", 110000,"MXN", "13.1",   30,  30));  // $1100
        records.add(pb("PB-M5-019", "MERCHANT_005",  27500,"COP", "13.3",   12,  12));  // $275
        records.add(pb("PB-M5-020", "MERCHANT_005",  68000,"BRL", "4841",   18,  18));  // $680

        // Extra MERCHANT_001–004 records via Processor B (to hit 150+ total and show both formats per merchant)
        records.add(pb("PB-M1-001", "MERCHANT_001",  98000,"BRL", "10.4",    3,   3));  // $980
        records.add(pb("PB-M1-002", "MERCHANT_001",  22000,"BRL", "4855",   10,  10));  // $220
        records.add(pb("PB-M1-003", "MERCHANT_001", 150000,"BRL", "4841",   -4,  -4));  // $1500
        records.add(pb("PB-M1-004", "MERCHANT_001",  7500, "BRL", "12.6",    7,   7));  // $75
        records.add(pb("PB-M1-005", "MERCHANT_001", 250000,"BRL", "4837",   25,  25));  // $2500

        records.add(pb("PB-M2-001", "MERCHANT_002",  33000,"MXN", "13.3",    2,   2));  // $330
        records.add(pb("PB-M2-002", "MERCHANT_002", 220000,"MXN", "10.4",   -7,  -7));  // $2200
        records.add(pb("PB-M2-003", "MERCHANT_002",  5500, "MXN", "4841",   11,  11));  // $55
        records.add(pb("PB-M2-004", "MERCHANT_002",  88000,"MXN", "4853",    5,   5));  // $880
        records.add(pb("PB-M2-005", "MERCHANT_002", 175000,"MXN", "4855",   16,  16));  // $1750

        records.add(pb("PB-M3-001", "MERCHANT_003",  42000,"COP", "4837",    1,   1));  // $420
        records.add(pb("PB-M3-002", "MERCHANT_003", 105000,"COP", "13.1",    6,   6));  // $1050
        records.add(pb("PB-M3-003", "MERCHANT_003",  1800, "COP", "13.2",   -3,  -3));  // $18
        records.add(pb("PB-M3-004", "MERCHANT_003", 340000,"COP", "12.6",   14,  14));  // $3400
        records.add(pb("PB-M3-005", "MERCHANT_003",  61000,"COP", "4853",    8,   8));  // $610

        records.add(pb("PB-M4-001", "MERCHANT_004",  125000,"USD","10.4",    2,   2));  // $1250
        records.add(pb("PB-M4-002", "MERCHANT_004",   55000,"USD","4855",   -9,  -9));  // $550
        records.add(pb("PB-M4-003", "MERCHANT_004",  210000,"USD","12.6",   11,  11));  // $2100
        records.add(pb("PB-M4-004", "MERCHANT_004",   35000,"USD","13.3",    5,   5));  // $350
        records.add(pb("PB-M4-005", "MERCHANT_004",  300000,"USD","4841",   22,  22));  // $3000

        // Extra records to guarantee 150+ total
        records.add(pb("PB-M5-021", "MERCHANT_005", 78000, "USD", "4853",    4,   4));
        records.add(pb("PB-M5-022", "MERCHANT_005", 12000, "BRL", "4841",  -15, -15));
        records.add(pb("PB-M5-023", "MERCHANT_005", 198000,"MXN", "10.4",    1,   1));
        records.add(pb("PB-M5-024", "MERCHANT_005", 88500, "COP", "13.1",   10,  10));
        records.add(pb("PB-M5-025", "MERCHANT_005", 32000, "USD", "12.6",   17,  17));
        records.add(pb("PB-M5-026", "MERCHANT_005", 15000, "BRL", "4837",    3,   3));
        records.add(pb("PB-M5-027", "MERCHANT_005", 260000,"COP", "4855",   -1,  -1));
        records.add(pb("PB-M5-028", "MERCHANT_005", 45500, "MXN", "13.3",    8,   8));
        records.add(pb("PB-M5-029", "MERCHANT_005", 115000,"USD", "4841",   13,  13));
        records.add(pb("PB-M5-030", "MERCHANT_005", 370000,"BRL", "10.4",   28,  28));

        return records;
    }

    // ---- Builder helpers ----

    /** Creates a Processor A request with disputeDate and deadline relative to today. */
    private ProcessorARequest pa(String disputeId, String merchantId, String amount,
                                  String currency, String reasonCode,
                                  int disputeDaysAgo, int deadlineDaysFromNow) {
        return new ProcessorARequest(
            disputeId, merchantId,
            new BigDecimal(amount),
            currency, reasonCode,
            TODAY.minusDays(Math.abs(disputeDaysAgo)),
            TODAY.plusDays(deadlineDaysFromNow),
            TODAY.minusDays(Math.abs(disputeDaysAgo) + 5),
            null, null
        );
    }

    /** Creates a Processor B request with amounts in cents and dates relative to today. */
    private ProcessorBRequest pb(String reference, String merchantRef, int amountCents,
                                  String currency, String reasonCode,
                                  int disputeDaysAgo, int deadlineDaysFromNow) {
        return new ProcessorBRequest(
            reference, merchantRef, amountCents, currency, reasonCode,
            TODAY.minusDays(Math.abs(disputeDaysAgo)),
            TODAY.plusDays(deadlineDaysFromNow),
            TODAY.minusDays(Math.abs(disputeDaysAgo) + 5),
            null, null
        );
    }
}
