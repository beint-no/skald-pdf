package org.skaldpdf;

import org.skaldpdf.barcode.Code128Barcode;
import org.skaldpdf.barcode.Ean13Barcode;
import org.skaldpdf.barcode.QrCode;
import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.colors.DeviceRgb;
import org.skaldpdf.colors.LinearGradient;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageDataFactory;
import org.skaldpdf.layout.Document;
import org.skaldpdf.layout.borders.Border;
import org.skaldpdf.layout.borders.SolidBorder;
import org.skaldpdf.layout.element.AreaBreak;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Div;
import org.skaldpdf.layout.canvas.SolidLine;
import org.skaldpdf.layout.element.Image;
import org.skaldpdf.layout.element.LineSeparator;
import org.skaldpdf.layout.element.ListBlock;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Deterministic corpus of one hundred generated documents for visual review. */
public final class ExampleGallery {
    private static final Theme FOREST = new Theme(
        ColorConstants.INK, ColorConstants.ACCENT, ColorConstants.SURFACE, ColorConstants.LINE, ColorConstants.MUTED
    );
    private static final Theme OCEAN = new Theme(
        DeviceRgb.hex("#10233A"), DeviceRgb.hex("#1F4E79"), DeviceRgb.hex("#F3F7FB"),
        DeviceRgb.hex("#D5E1EC"), DeviceRgb.hex("#5B7088")
    );
    private static final Theme SLATE = new Theme(
        DeviceRgb.hex("#1B1D21"), DeviceRgb.hex("#3F4A55"), DeviceRgb.hex("#F6F6F4"),
        DeviceRgb.hex("#DDDDD8"), DeviceRgb.hex("#6B7178")
    );
    private static final Theme EMBER = new Theme(
        DeviceRgb.hex("#2A1610"), DeviceRgb.hex("#B85C38"), DeviceRgb.hex("#FBF6F1"),
        DeviceRgb.hex("#E8D7C8"), DeviceRgb.hex("#8A6A58")
    );
    private static final String BODY = "Skald emits compact PDF 2.0 with embedded Unicode fonts, incremental "
        + "layout, and no third-party runtime dependencies. The writer prefers object streams, subset fonts, "
        + "and shared image resources so ordinary business documents stay small and predictable.";

    private ExampleGallery() {
    }

    public static void main(String[] arguments) throws Exception {
        var target = arguments.length > 0
            ? Path.of(arguments[0])
            : Path.of(System.getProperty("user.home"), "Downloads", "skald-examples");
        System.out.println("Wrote " + writeAll(target) + " PDFs to " + target.toAbsolutePath());
    }

    public static int writeAll(Path directory) throws IOException {
        Files.createDirectories(directory);
        var examples = examples();
        if (examples.size() != 100) {
            throw new IllegalStateException("Example gallery must contain 100 documents, found " + examples.size());
        }
        for (var example : examples) {
            Files.write(directory.resolve(example.name() + ".pdf"), example.bytes());
        }
        return examples.size();
    }

    static List<NamedPdf> examples() {
        var examples = new ArrayList<NamedPdf>();
        examples.add(pdf("01-invoice-modern", invoice(FOREST, "2026-1001", "Consulting August", 12_500)));
        examples.add(pdf("02-invoice-retainer", invoice(OCEAN, "2026-1044", "Monthly retainer", 48_000)));
        examples.add(pdf("03-credit-note", creditNote()));
        examples.add(pdf("04-quote", quote()));
        examples.add(pdf("05-sales-order", salesOrder()));
        examples.add(pdf("06-purchase-order", purchaseOrder()));
        examples.add(pdf("07-packing-slip", packingSlip()));
        examples.add(pdf("08-delivery-note", deliveryNote()));
        examples.add(pdf("09-receipt-a5", receiptA5()));
        examples.add(pdf("10-thermal-receipt", thermalReceipt()));
        examples.add(pdf("11-payment-reminder", reminder()));
        examples.add(pdf("12-statement-of-account", statement()));
        examples.add(pdf("13-collection-notice", collectionNotice()));
        examples.add(pdf("14-payslip", payslip()));
        examples.add(pdf("15-expense-report", expenseReport()));
        examples.add(pdf("16-timesheet", timesheet()));
        examples.add(pdf("17-travel-expense", travelExpense()));
        examples.add(pdf("18-tax-receipt", taxReceipt()));
        examples.add(pdf("19-donation-receipt", donationReceipt()));
        examples.add(pdf("20-balance-sheet", financial("Balance sheet", "Assets", "Equity and liabilities")));
        examples.add(pdf("21-profit-loss", financial("Profit and loss", "Income", "Expenses")));
        examples.add(pdf("22-cash-flow", financial("Cash flow", "Operations", "Financing")));
        examples.add(pdf("23-budget-vs-actual", budgetVsActual()));
        examples.add(pdf("24-forecast", forecast()));
        examples.add(pdf("25-general-ledger", ledger()));
        examples.add(pdf("26-trial-balance", trialBalance()));
        examples.add(pdf("27-bank-statement", bankStatement()));
        examples.add(pdf("28-reconciliation", reconciliation()));
        examples.add(pdf("29-kpi-dashboard", kpiDashboard()));
        examples.add(pdf("30-weekly-status", weeklyStatus()));
        examples.add(pdf("31-project-proposal", proposal()));
        examples.add(pdf("32-project-timeline", timeline()));
        examples.add(pdf("33-meeting-agenda", agenda()));
        examples.add(pdf("34-meeting-minutes", minutes()));
        examples.add(pdf("35-board-resolution", boardResolution()));
        examples.add(pdf("36-shareholder-notice", shareholderNotice()));
        examples.add(pdf("37-service-agreement", agreement("Service agreement", "scope, fees, and termination")));
        examples.add(pdf("38-nda", agreement("Mutual NDA", "confidential information and residual knowledge")));
        examples.add(pdf("39-terms-of-service", agreement("Terms of service", "acceptable use and limitation of liability")));
        examples.add(pdf("40-privacy-summary", privacySummary()));
        examples.add(pdf("41-offer-letter", offerLetter()));
        examples.add(pdf("42-employment-contract", agreement("Employment contract", "role, salary, and notice")));
        examples.add(pdf("43-cover-letter", coverLetter()));
        examples.add(pdf("44-resume", resume()));
        examples.add(pdf("45-certificate", certificate()));
        examples.add(pdf("46-diploma", diploma()));
        examples.add(pdf("47-warranty", warranty()));
        examples.add(pdf("48-inspection-report", inspection()));
        examples.add(pdf("49-audit-findings", auditFindings()));
        examples.add(pdf("50-risk-register", riskRegister()));
        examples.add(pdf("51-inventory-list", inventory()));
        examples.add(pdf("52-stock-valuation", stockValuation()));
        examples.add(pdf("53-picking-list", pickingList()));
        examples.add(pdf("54-shipping-label", shippingLabel()));
        examples.add(pdf("55-product-label-ean", productLabel()));
        examples.add(pdf("56-shelf-label", shelfLabel()));
        examples.add(pdf("57-price-list", priceList()));
        examples.add(pdf("58-catalog-page", catalogPage()));
        examples.add(pdf("59-comparison-table", comparison()));
        examples.add(pdf("60-menu", menu()));
        examples.add(pdf("61-recipe-card", recipe()));
        examples.add(pdf("62-newsletter", newsletter()));
        examples.add(pdf("63-magazine-article", magazine()));
        examples.add(pdf("64-justified-essay", essay()));
        examples.add(pdf("65-white-paper", whitePaper()));
        examples.add(pdf("66-case-study", caseStudy()));
        examples.add(pdf("67-release-notes", releaseNotes()));
        examples.add(pdf("68-changelog", changelog()));
        examples.add(pdf("69-spec-sheet", specSheet()));
        examples.add(pdf("70-architecture-onepager", architecture()));
        examples.add(pdf("71-safety-brief", safetyBrief()));
        examples.add(pdf("72-onboarding-checklist", checklist()));
        examples.add(pdf("73-attendance-sheet", attendance()));
        examples.add(pdf("74-seating-chart", seating()));
        examples.add(pdf("75-calendar-august", calendar()));
        examples.add(pdf("76-event-ticket", ticket()));
        examples.add(pdf("77-boarding-pass", boardingPass()));
        examples.add(pdf("78-badge", badge()));
        examples.add(pdf("79-business-card", businessCard()));
        examples.add(pdf("80-postcard", postcard()));
        examples.add(pdf("81-flyer", flyer()));
        examples.add(pdf("82-poster-a3", poster()));
        examples.add(pdf("83-brochure", brochure()));
        examples.add(pdf("84-invitation", invitation()));
        examples.add(pdf("85-thank-you", thankYou()));
        examples.add(pdf("86-memo", memo()));
        examples.add(pdf("87-letter", letter()));
        examples.add(pdf("88-itinerary", itinerary()));
        examples.add(pdf("89-hotel-folio", hotelFolio()));
        examples.add(pdf("90-survey-results", survey()));
        examples.add(pdf("91-quiz-results", quiz()));
        examples.add(pdf("92-process-steps", processSteps()));
        examples.add(pdf("93-org-boxes", orgBoxes()));
        examples.add(pdf("94-heatmap-table", heatmap()));
        examples.add(pdf("95-watermarked-preview", watermarked()));
        examples.add(pdf("96-gradient-cover", gradientCover()));
        examples.add(pdf("97-barcode-pack", barcodePack()));
        examples.add(pdf("98-merged-pack", mergedPack()));
        examples.add(pdf("99-multi-page-report", multiPageReport()));
        examples.add(pdf("100-style-guide", styleGuide()));
        return List.copyOf(examples);
    }

    private static NamedPdf pdf(String name, byte[] bytes) {
        return new NamedPdf(name, bytes);
    }

    private static byte[] create(String title, Consumer<Document> content) {
        return create(PageSize.A4, FOREST, 48, title, content);
    }

    private static byte[] create(PageSize pageSize, Theme theme, float margin, String title,
                                 Consumer<Document> content) {
        var compact = pageSize.getHeight() < 400 || pageSize.getWidth() < 400;
        return Pdf.create(pageSize, document -> {
            document.setTitle(title)
                .setAuthor("Skald PDF")
                .setSubject(title)
                .setKeywords("example, skald")
                .setLanguage("en-GB")
                .setFontSize(10.5f)
                .setMargins(margin, margin, margin, margin);
            if (!compact) {
                document.setHeader(18, page -> new Paragraph("Skald · " + title)
                    .setFontSize(8).setFontColor(theme.muted()));
                document.setFooter(16, page -> new Paragraph(page.pageNumber() + " / " + page.pageCount())
                    .setFontSize(8).setFontColor(theme.muted()).setTextAlignment(TextAlignment.CENTER));
            }
            content.accept(document);
        });
    }

    private static byte[] invoice(Theme theme, String number, String description, int amount) {
        return create(PageSize.A4, theme, 44, "Invoice " + number, document -> {
            document.setFirstHeader(page -> new Paragraph("Northstar Ledger AS · original invoice")
                .setFontSize(8).setFontColor(theme.accent()));
            banner(document, theme, "Invoice", number);
            parties(document, theme);
            var lines = styledTable(theme, new float[] {4, 1, 1.4f, 1.4f}, "Description", "Qty", "Rate", "Amount");
            lines.addRow(description, "1", money(amount), money(amount));
            lines.addRow("Platform access", "1", "1 200.00", "1 200.00");
            document.add(lines.setMarginTop(18));
            var total = new Table(UnitValue.createPercentArray(new float[] {1, 3})).useAllAvailableWidth()
                .setBorder(Border.NO_BORDER).setMarginTop(14);
            total.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Image(new QrCode("https://pay.skaldpdf.org/inv/" + number).withModuleSize(2.4f))
                    .scaleToFit(72, 72)));
            total.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph("Total NOK " + money(amount + 1_200)).bold().setFontSize(14)
                    .setFontColor(theme.accent()).setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("Scan to pay · Due 26 August 2026 · Account 1503.45.67890")
                    .setFontSize(9).setFontColor(theme.muted()).setTextAlignment(TextAlignment.RIGHT).setMarginTop(6))
                .add(new Paragraph("Payment terms: net 14 days.").italic()
                    .setFontSize(9).setFontColor(theme.muted()).setTextAlignment(TextAlignment.RIGHT).setMarginTop(4)));
            document.add(total);
        });
    }

    private static byte[] creditNote() {
        return create("Credit note 2026-12", document -> {
            banner(document, EMBER, "Credit note", "2026-12");
            document.add(new Paragraph("Credits invoice 2026-1001 for an overbilled retainer day."));
            var table = styledTable(EMBER, new float[] {4, 2}, "Reason", "Amount");
            table.addRow("Unused advisory day", "-6 250.00");
            document.add(table.setMarginTop(16));
        });
    }

    private static byte[] quote() {
        return create("Quote Q-884", document -> {
            banner(document, OCEAN, "Quote", "Q-884");
            document.add(new Paragraph(BODY).justify());
            var table = styledTable(OCEAN, new float[] {4, 1, 1.4f}, "Workstream", "Days", "Fee");
            table.addRow("Discovery", "4", "20 000.00");
            table.addRow("Implementation", "10", "50 000.00");
            table.addRow("Handover", "2", "10 000.00");
            document.add(table.setMarginTop(16));
            document.add(new ListBlock().add("Valid for 30 days").add("Excludes VAT").add("Start date by agreement"));
        });
    }

    private static byte[] salesOrder() {
        return create("Sales order SO-310", document -> {
            heading(document, FOREST, "Sales order SO-310");
            var table = styledTable(FOREST, new float[] {3, 2, 1, 1}, "SKU", "Title", "Qty", "Ship");
            for (int index = 1; index <= 8; index++) {
                table.addRow("SKU-%03d".formatted(index), "Nordic ceramic bowl " + index, "2", "Oslo");
            }
            document.add(table);
        });
    }

    private static byte[] purchaseOrder() {
        return create("Purchase order PO-77", document -> {
            heading(document, SLATE, "Purchase order PO-77");
            document.add(muted("Supplier: Fjord Paper Mill AS"));
            var table = styledTable(SLATE, new float[] {3, 2, 1, 1.4f}, "Item", "Spec", "Qty", "Amount");
            table.addRow("Letter stock", "120 gsm A4", "20", "4 800.00");
            table.addRow("Envelope", "C5 recycled", "20", "960.00");
            document.add(table.setMarginTop(12));
        });
    }

    private static byte[] packingSlip() {
        return create("Packing slip", document -> {
            heading(document, FOREST, "Packing slip · #4412");
            document.add(new Paragraph("Ship to Nordlys Butikk AS, Storgata 10, Oslo"));
            var table = styledTable(FOREST, new float[] {2, 4, 1}, "SKU", "Item", "Qty");
            table.addRow("SKU-018", "Oak tray", "3");
            table.addRow("SKU-044", "Linen napkin set", "6");
            document.add(table.setMarginTop(12));
            document.add(new Image(new QrCode("https://track.skaldpdf.org/4412").withModuleSize(2.2f))
                .scaleToFit(64, 64).setMarginTop(16));
            document.add(muted("Scan for live tracking"));
        });
    }

    private static byte[] deliveryNote() {
        return create("Delivery note", document -> {
            heading(document, OCEAN, "Delivery note DN-90");
            document.add(new ListBlock(ListBlock.Marker.DECIMAL)
                .add("Left warehouse 07:10")
                .add("Scanned at terminal 08:02")
                .add("Delivered and signed 09:41"));
        });
    }

    private static byte[] receiptA5() {
        return create(PageSize.A5, EMBER, 28, "Receipt", document -> {
            heading(document, EMBER, "Payment received");
            document.add(new Paragraph("NOK 1 562.50 · Visa · 12.08.2026 07:43").bold());
            document.add(muted("Reference INV-2026-1001"));
        });
    }

    private static byte[] thermalReceipt() {
        return create(PageSize.RECEIPT_80MM, SLATE, 12, "POS receipt", document -> {
            document.add(new Paragraph("NORTHSTAR COFFEE").bold().setFontSize(13).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Terminal 2 · 0001042").setFontSize(8).setTextAlignment(TextAlignment.CENTER));
            var table = styledTable(SLATE, new float[] {4, 1.4f}, "Item", "NOK");
            table.addRow("Oat latte", "58");
            table.addRow("Cardamom bun", "45");
            document.add(table.setMarginTop(8));
            document.add(new Paragraph("TOTAL 103").bold().setTextAlignment(TextAlignment.RIGHT));
        });
    }

    private static byte[] reminder() {
        return create("Payment reminder", document -> {
            heading(document, EMBER, "Payment reminder");
            document.add(new Paragraph("Invoice 2026-1001 is 17 days overdue. Please pay NOK 10 117.47."));
            var table = styledTable(EMBER, new float[] {2, 3, 2}, "Date", "Event", "Balance");
            table.addRow("12.07", "Invoice", "12 500.00");
            table.addRow("01.08", "Partial payment", "10 000.00");
            table.addRow("12.08", "Interest and fee", "10 117.47");
            document.add(table.setMarginTop(12));
        });
    }

    private static byte[] statement() {
        return create("Statement of account", document -> {
            heading(document, FOREST, "Statement of account");
            var table = styledTable(FOREST, new float[] {1.4f, 3, 1.3f, 1.3f}, "Date", "Text", "Debit", "Credit");
            for (int index = 1; index <= 12; index++) {
                table.addRow("%02d.07".formatted(index), "Settlement " + index,
                    index % 2 == 0 ? "1 250.00" : "", index % 2 == 0 ? "" : "1 250.00");
            }
            document.add(table);
        });
    }

    private static byte[] collectionNotice() {
        return create("Collection notice", document -> {
            heading(document, EMBER, "Final notice before collection");
            document.add(new Paragraph(BODY).justify());
            document.add(new Paragraph("Outstanding NOK 10 117.47").bold().setFontSize(14).setMarginTop(10));
        });
    }

    private static byte[] payslip() {
        return create("Payslip", document -> {
            heading(document, OCEAN, "Payslip · August 2026");
            var table = styledTable(OCEAN, new float[] {3, 2, 2}, "Component", "Amount", "YTD");
            table.addRow("Monthly salary", "55 000", "440 000");
            table.addRow("Tax", "-16 500", "-132 000");
            table.addRow("Pension", "-1 100", "-8 800");
            document.add(table);
            document.add(new Paragraph("Net pay NOK 37 400").bold().setMarginTop(10));
        });
    }

    private static byte[] expenseReport() {
        return create("Expense report", document -> {
            heading(document, SLATE, "Expense report · EX-204");
            var table = styledTable(SLATE, new float[] {2, 3, 1.4f}, "Date", "Merchant", "Amount");
            table.addRow("03.08", "NSB", "214.00");
            table.addRow("04.08", "Fjord Hotel", "1 890.00");
            table.addRow("05.08", "Deli Nord", "168.00");
            document.add(table);
        });
    }

    private static byte[] timesheet() {
        return create("Timesheet", document -> {
            heading(document, FOREST, "Timesheet · week 32");
            var table = styledTable(FOREST, new float[] {2, 3, 1}, "Day", "Project", "Hours");
            for (var day : List.of("Mon", "Tue", "Wed", "Thu", "Fri")) {
                table.addRow(day, "Skald layout", "7.5");
            }
            document.add(table);
        });
    }

    private static byte[] travelExpense() {
        return create("Travel expense", document -> {
            heading(document, EMBER, "Travel · Stockholm");
            document.add(new ListBlock(ListBlock.Marker.DASH)
                .add("Outbound DY814 · 06:25")
                .add("Hotel Skeppsholmen · 2 nights")
                .add("Return DY819 · 18:10"));
        });
    }

    private static byte[] taxReceipt() {
        return create("Tax receipt", document -> {
            heading(document, SLATE, "Tax payment receipt 2025");
            document.add(new Paragraph("Organisation 999 888 777 · Paid NOK 184 220.00"));
            document.add(muted("Validation result: accepted"));
        });
    }

    private static byte[] donationReceipt() {
        return create("Donation receipt", document -> {
            heading(document, FOREST, "Thank you for your donation");
            document.add(new Paragraph("NOK 2 000 to Fjord Nature Trust is deductible under Norwegian rules."));
        });
    }

    private static byte[] financial(String title, String left, String right) {
        return create(title, document -> {
            heading(document, FOREST, title + " 2025");
            var table = styledTable(FOREST, new float[] {3, 2, 3, 2}, left, "NOK", right, "NOK");
            table.addRow("Cash", "420 000", "Share capital", "100 000");
            table.addRow("Receivables", "188 000", "Retained earnings", "390 000");
            table.addRow("Equipment", "96 000", "Payables", "214 000");
            document.add(table);
        });
    }

    private static byte[] budgetVsActual() {
        return create("Budget vs actual", document -> {
            heading(document, OCEAN, "Budget vs actual · Q2");
            var table = styledTable(OCEAN, new float[] {3, 2, 2, 2}, "Account", "Budget", "Actual", "Var");
            table.addRow("Revenue", "1 200 000", "1 184 200", "-1.3%");
            table.addRow("Payroll", "640 000", "652 100", "+1.9%");
            table.addRow("Software", "48 000", "44 800", "-6.7%");
            document.add(table);
        });
    }

    private static byte[] forecast() {
        return create("Forecast", document -> {
            heading(document, SLATE, "Twelve-month forecast");
            document.add(new Paragraph(BODY).justify());
            var table = styledTable(SLATE, new float[] {2, 2, 2, 2}, "Q1", "Q2", "Q3", "Q4");
            table.addRow("2.1m", "2.4m", "2.6m", "2.9m");
            document.add(table.setMarginTop(12));
        });
    }

    private static byte[] ledger() {
        return create(PageSize.A4.landscape(), FOREST, 28, "General ledger", document -> {
            heading(document, FOREST, "General ledger");
            var table = styledTable(FOREST, new float[] {1.2f, 1.4f, 3, 1.3f, 1.3f},
                "Date", "Voucher", "Text", "Debit", "Credit");
            for (int index = 1; index <= 18; index++) {
                table.addRow("12.08", "V-%03d".formatted(index), "Posted transaction " + index,
                    index % 2 == 0 ? "1 250.00" : "", index % 2 == 0 ? "" : "1 250.00");
            }
            document.add(table);
        });
    }

    private static byte[] trialBalance() {
        return create("Trial balance", document -> {
            heading(document, FOREST, "Trial balance");
            var table = styledTable(FOREST, new float[] {1, 4, 2, 2}, "No", "Account", "Debit", "Credit");
            table.addRow("1500", "Customers", "188 000", "");
            table.addRow("1920", "Bank", "420 000", "");
            table.addRow("2400", "Suppliers", "", "214 000");
            document.add(table);
        });
    }

    private static byte[] bankStatement() {
        return create("Bank statement", document -> {
            heading(document, OCEAN, "Bank statement · 1503.45.67890");
            var table = styledTable(OCEAN, new float[] {1.4f, 4, 2}, "Date", "Text", "Amount");
            table.addRow("01.08", "Opening balance", "219 440.12");
            table.addRow("04.08", "Incoming payment Nordlys", "12 500.00");
            table.addRow("11.08", "Payroll", "-148 200.00");
            document.add(table);
        });
    }

    private static byte[] reconciliation() {
        return create("Reconciliation", document -> {
            heading(document, SLATE, "Bank reconciliation");
            var table = styledTable(SLATE, new float[] {4, 2}, "Item", "Amount");
            table.addRow("Book balance", "84 112.40");
            table.addRow("Outstanding cheques", "-1 200.00");
            table.addRow("Bank balance", "82 912.40");
            document.add(table);
        });
    }

    private static byte[] kpiDashboard() {
        return create("KPI dashboard", document -> {
            heading(document, FOREST, "August operating pulse");
            var cards = new Table(3).useAllAvailableWidth().setBorder(Border.NO_BORDER);
            cards.addCell(kpi(FOREST, "Gross margin", "61.4%"));
            cards.addCell(kpi(OCEAN, "NPS", "64"));
            cards.addCell(kpi(EMBER, "Churn", "1.8%"));
            document.add(cards);
        });
    }

    private static Cell kpi(Theme theme, String label, String value) {
        return new Cell()
            .setBorder(Border.NO_BORDER)
            .setBackgroundColor(theme.surface())
            .setBorderRadius(10)
            .setPadding(12)
            .add(new Paragraph(label).setFontSize(9).setFontColor(theme.muted()))
            .add(new Paragraph(value).bold().setFontSize(22).setFontColor(theme.accent()));
    }

    private static byte[] weeklyStatus() {
        return create("Weekly status", document -> {
            heading(document, OCEAN, "Week 32 status");
            document.add(new ListBlock(ListBlock.Marker.DECIMAL)
                .add("Shipped page templates and justified text.")
                .add("Code 128 verified with an independent decoder.")
                .add("Next: streaming spool for very large files."));
        });
    }

    private static byte[] proposal() {
        return create("Project proposal", document -> {
            heading(document, FOREST, "Proposal · Ledger rebuild");
            document.add(new Paragraph(BODY).justify());
            document.add(new ListBlock().add("Eight weeks").add("Fixed fee NOK 480 000").add("Two engineers"));
        });
    }

    private static byte[] timeline() {
        return create("Project timeline", document -> {
            heading(document, SLATE, "Delivery timeline");
            var table = styledTable(SLATE, new float[] {2, 2, 4}, "Phase", "When", "Outcome");
            table.addRow("Discover", "Week 1-2", "Scope and metrics");
            table.addRow("Build", "Week 3-6", "Working documents");
            table.addRow("Handover", "Week 7-8", "Runbooks");
            document.add(table);
        });
    }

    private static byte[] agenda() {
        return create("Meeting agenda", document -> {
            heading(document, FOREST, "Leadership agenda · 13 August");
            document.add(new ListBlock(ListBlock.Marker.DECIMAL)
                .add("Q2 numbers")
                .add("Hiring plan")
                .add("Office move")
                .add("AOB"));
        });
    }

    private static byte[] minutes() {
        return create("Meeting minutes", document -> {
            heading(document, OCEAN, "Minutes · 13 August");
            document.add(new Paragraph("Present: Ada, Jonas, Liv. Decision: proceed with Skald as the document engine.")
                .justify());
        });
    }

    private static byte[] boardResolution() {
        return create("Board resolution", document -> {
            heading(document, SLATE, "Board resolution 2026/14");
            document.add(new Paragraph("The board resolves to adopt Skald PDF for all generated customer documents.")
                .justify());
            document.add(muted("Signed in Oslo on 13 August 2026"));
        });
    }

    private static byte[] shareholderNotice() {
        return create("Shareholder notice", document -> {
            heading(document, FOREST, "Notice of general meeting");
            document.add(new Paragraph("The annual general meeting will be held 22 September 2026 at 15:00.")
                .justify());
        });
    }

    private static byte[] agreement(String title, String topic) {
        return create(title, document -> {
            heading(document, SLATE, title);
            var sections = List.of(
                "Parties and purpose",
                Character.toUpperCase(topic.charAt(0)) + topic.substring(1),
                "Fees and payment",
                "Confidentiality",
                "Term and termination",
                "Governing law"
            );
            for (int section = 0; section < sections.size(); section++) {
                document.add(new Paragraph((section + 1) + ". " + sections.get(section))
                    .bold().setKeepWithNext(true).setMarginTop(8));
                document.add(new Paragraph(BODY).justify().setFontSize(10));
            }
        });
    }

    private static byte[] privacySummary() {
        return create("Privacy summary", document -> {
            heading(document, OCEAN, "Privacy, in one page");
            document.add(new ListBlock()
                .add("We store invoices for seven years.")
                .add("We do not sell personal data.")
                .add("You can export or delete your account."));
        });
    }

    private static byte[] offerLetter() {
        return create("Offer letter", document -> {
            heading(document, FOREST, "Offer of employment");
            document.add(new Paragraph("Dear Liv, we are pleased to offer you the role of Product Designer.")
                .justify());
            document.add(new Paragraph("Start date 1 September 2026 · NOK 780 000.").setMarginTop(8));
        });
    }

    private static byte[] coverLetter() {
        return create("Cover letter", document -> {
            heading(document, SLATE, "Application");
            document.add(new Paragraph("I am writing to apply for the staff engineer role. "
                + BODY).justify());
        });
    }

    private static byte[] resume() {
        return create("Resume", document -> {
            heading(document, FOREST, "Ada Holm");
            document.add(muted("Document systems · Oslo"));
            document.add(new Paragraph("Experience").bold().setMarginTop(10));
            document.add(new ListBlock()
                .add("Northstar Ledger — Staff engineer, 2022–")
                .add("Fjord Studio — Senior engineer, 2018–2022"));
        });
    }

    private static byte[] certificate() {
        return create(PageSize.A4.landscape(), EMBER, 48, "Certificate", document -> {
            document.add(new Div()
                .setBorder(new SolidBorder(EMBER.accent(), 2))
                .setBorderRadius(8)
                .setPadding(36)
                .add(new Paragraph("Certificate of completion").bold().setFontSize(28)
                    .setTextAlignment(TextAlignment.CENTER).setFontColor(EMBER.accent()))
                .add(new Paragraph("This certifies that Jonas Berg completed Skald document design.")
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(16)));
        });
    }

    private static byte[] diploma() {
        return create(PageSize.A4.landscape(), FOREST, 48, "Diploma", document -> {
            heading(document, FOREST, "Diploma");
            document.add(new Paragraph("Awarded with distinction for a complete PDF 2.0 implementation.")
                .setTextAlignment(TextAlignment.CENTER).setFontSize(14));
        });
    }

    private static byte[] warranty() {
        return create("Warranty", document -> {
            heading(document, SLATE, "Limited warranty");
            document.add(new Paragraph("Hardware is warranted for 24 months from the date of purchase.").justify());
        });
    }

    private static byte[] inspection() {
        return create("Inspection report", document -> {
            heading(document, OCEAN, "Warehouse inspection");
            var table = styledTable(OCEAN, new float[] {3, 2, 3}, "Check", "Result", "Note");
            table.addRow("Fire exits", "Pass", "Clear");
            table.addRow("Racking", "Watch", "Aisle 4 leaning");
            table.addRow("Cold store", "Pass", "2.1 °C");
            document.add(table);
        });
    }

    private static byte[] auditFindings() {
        return create("Audit findings", document -> {
            heading(document, EMBER, "Internal audit findings");
            document.add(new ListBlock(ListBlock.Marker.DECIMAL)
                .add("Two invoices lacked a purchase order.")
                .add("Access reviews are quarterly, not monthly.")
                .add("Backup restore was last tested in March."));
        });
    }

    private static byte[] riskRegister() {
        return create("Risk register", document -> {
            heading(document, SLATE, "Risk register");
            var table = styledTable(SLATE, new float[] {3, 1, 1, 3}, "Risk", "P", "I", "Mitigation");
            table.addRow("Key person", "3", "4", "Pair on writer internals");
            table.addRow("Font coverage", "2", "3", "Fallback module later");
            document.add(table);
        });
    }

    private static byte[] inventory() {
        return create("Inventory list", document -> {
            heading(document, FOREST, "Stock list");
            var table = styledTable(FOREST, new float[] {3, 2, 1, 2}, "Item", "SKU", "Qty", "Value");
            for (int index = 1; index <= 16; index++) {
                table.addRow("Variant " + index, "SKU-%03d".formatted(index), Integer.toString(index + 3), money(index * 125));
            }
            document.add(table);
        });
    }

    private static byte[] stockValuation() {
        return create("Stock valuation", document -> {
            heading(document, OCEAN, "Stock valuation");
            document.add(new Paragraph("Weighted average cost · NOK 184 420.50").bold());
        });
    }

    private static byte[] pickingList() {
        return create("Picking list", document -> {
            heading(document, SLATE, "Pick wave 14");
            var table = styledTable(SLATE, new float[] {1, 2, 3, 1}, "Loc", "SKU", "Title", "Qty");
            table.addRow("A-01", "SKU-018", "Oak tray", "3");
            table.addRow("B-12", "SKU-044", "Linen napkin", "6");
            document.add(table);
        });
    }

    private static byte[] shippingLabel() {
        return create(new PageSize(320, 220), FOREST, 16, "Shipping label", document -> {
            document.add(new Paragraph("TO").setFontSize(8).setFontColor(FOREST.muted()));
            document.add(new Paragraph("Nordlys Butikk AS\nStorgata 10\n0184 Oslo").bold().setFontSize(12));
            document.add(new Image(new Code128Barcode("NO4412OSL")
                .withModuleWidth(1.1f).withBarHeight(36f)).scaleToFit(260, 70).setMarginTop(10));
        });
    }

    private static byte[] productLabel() {
        return create(new PageSize(280, 180), FOREST, 14, "Product label", document -> {
            document.add(new Paragraph("Oak serving board").bold().setFontSize(13));
            document.add(new Image(new Ean13Barcode("5901234123457")
                .withModuleWidth(1.15f).withBarHeight(40f)).scaleToFit(240, 80));
        });
    }

    private static byte[] shelfLabel() {
        return create(new PageSize(200, 90), EMBER, 8, "Shelf label", document -> {
            document.add(new Paragraph("Oat latte").bold().setFontSize(14));
            document.add(new Paragraph("NOK 58").setFontSize(18).setFontColor(EMBER.accent()));
        });
    }

    private static byte[] priceList() {
        return create("Price list", document -> {
            heading(document, FOREST, "Autumn price list");
            var table = styledTable(FOREST, new float[] {4, 2}, "Service", "NOK");
            table.addRow("Half day advisory", "6 250");
            table.addRow("Full day advisory", "11 500");
            table.addRow("Retainer", "48 000");
            document.add(table);
        });
    }

    private static byte[] catalogPage() {
        return create("Catalog page", document -> {
            heading(document, OCEAN, "Objects for the table");
            try {
                document.add(new Image(ImageDataFactory.create(PdfTestSupport.sampleLogo())).scaleToFit(180, 60));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            document.add(new Paragraph("Handmade in small batches. Oak, linen, and stoneware.").justify().setMarginTop(10));
        });
    }

    private static byte[] comparison() {
        return create("Comparison table", document -> {
            heading(document, SLATE, "Plan comparison");
            var table = styledTable(SLATE, new float[] {3, 2, 2, 2}, "Capability", "Start", "Team", "Firm");
            table.addRow("PDF 2.0 writer", "Yes", "Yes", "Yes");
            table.addRow("Page chrome", "Yes", "Yes", "Yes");
            table.addRow("Custom fonts", "—", "Yes", "Yes");
            document.add(table);
        });
    }

    private static byte[] menu() {
        return create(PageSize.A5, EMBER, 28, "Menu", document -> {
            heading(document, EMBER, "Evening menu");
            document.add(new Paragraph("Sourdough, brown butter").setFontSize(12));
            document.add(muted("NOK 145"));
            document.add(new Paragraph("Halibut, mussels, dill").setFontSize(12).setMarginTop(8));
            document.add(muted("NOK 295"));
        });
    }

    private static byte[] recipe() {
        return create(PageSize.A5, FOREST, 28, "Recipe", document -> {
            heading(document, FOREST, "Cardamom buns");
            document.add(new ListBlock().add("500 g flour").add("75 g butter").add("12 pods cardamom"));
        });
    }

    private static byte[] newsletter() {
        return create("Newsletter", document -> {
            heading(document, OCEAN, "Northstar notes · August");
            document.add(new Paragraph(BODY).justify());
            document.add(new Paragraph("Also this month: a quieter invoice, a clearer payslip.").setMarginTop(8));
        });
    }

    private static byte[] magazine() {
        return create("Magazine article", document -> {
            heading(document, SLATE, "Why generated documents still matter");
            document.add(new Paragraph(BODY.repeat(3)).justify().setMultipliedLeading(1.45f));
        });
    }

    private static byte[] essay() {
        return create("Justified essay", document -> {
            heading(document, FOREST, "On compact writers");
            document.add(new Paragraph((BODY + " ").repeat(8)).justify().setMultipliedLeading(1.42f));
        });
    }

    private static byte[] whitePaper() {
        return create("White paper", document -> {
            heading(document, OCEAN, "A modern PDF subset");
            document.add(new Paragraph(BODY).justify());
            document.add(new Paragraph("The useful surface is invoices, statements, and reports—not a browser.")
                .justify().setMarginTop(8));
        });
    }

    private static byte[] caseStudy() {
        return create("Case study", document -> {
            heading(document, FOREST, "Case study · Nordlys Handel");
            document.add(new Paragraph("Replacing a legacy writer cut invoice size by 38% and removed a dependency tree.")
                .justify());
        });
    }

    private static byte[] releaseNotes() {
        return create("Release notes", document -> {
            heading(document, SLATE, "Skald 0.3");
            document.add(new ListBlock(ListBlock.Marker.DECIMAL)
                .add("Page templates")
                .add("Justified text")
                .add("Code 128")
                .add("Axial gradients"));
        });
    }

    private static byte[] changelog() {
        return create("Changelog", document -> {
            heading(document, FOREST, "Changelog");
            var table = styledTable(FOREST, new float[] {2, 4}, "Date", "Change");
            table.addRow("13.08", "Public modular release");
            table.addRow("13.08", "Page chrome and lists");
            document.add(table);
        });
    }

    private static byte[] specSheet() {
        return create("Spec sheet", document -> {
            heading(document, OCEAN, "Skald layout module");
            var table = styledTable(OCEAN, new float[] {2, 4}, "Item", "Value");
            table.addRow("Runtime", "JDK 25+, zero third-party deps");
            table.addRow("Output", "PDF 2.0 only");
            table.addRow("Fonts", "Embedded subset TrueType");
            document.add(table);
        });
    }

    private static byte[] architecture() {
        return create("Architecture", document -> {
            heading(document, SLATE, "Architecture");
            document.add(new Paragraph("layout -> core -> java.desktop").bold().setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("barcode -> core").bold().setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(BODY).justify().setMarginTop(12));
        });
    }

    private static byte[] safetyBrief() {
        return create("Safety brief", document -> {
            heading(document, EMBER, "Studio safety brief");
            document.add(new ListBlock(ListBlock.Marker.DECIMAL)
                .add("Keep aisles clear.")
                .add("Report damaged racking.")
                .add("No unattended heat presses."));
        });
    }

    private static byte[] checklist() {
        return create("Onboarding checklist", document -> {
            heading(document, FOREST, "First-week checklist");
            document.add(new ListBlock()
                .add("Laptop and keys")
                .add("Payroll form")
                .add("Shadow a support shift")
                .add("Ship a tiny document change"));
        });
    }

    private static byte[] attendance() {
        return create("Attendance sheet", document -> {
            heading(document, SLATE, "Workshop attendance");
            var table = styledTable(SLATE, new float[] {3, 2, 2}, "Name", "In", "Out");
            table.addRow("Ada Holm", "09:00", "16:00");
            table.addRow("Jonas Berg", "09:05", "15:40");
            table.addRow("Liv Nilsen", "09:00", "16:00");
            document.add(table);
        });
    }

    private static byte[] seating() {
        return create("Seating chart", document -> {
            heading(document, OCEAN, "Dinner seating");
            var table = styledTable(OCEAN, new float[] {1, 3, 3}, "Table", "Guest", "Guest");
            table.addRow("1", "Ada Holm", "Jonas Berg");
            table.addRow("2", "Liv Nilsen", "Kai Strom");
            document.add(table);
        });
    }

    private static byte[] calendar() {
        return create("Calendar", document -> {
            heading(document, FOREST, "August 2026");
            var table = styledTable(FOREST, new float[] {1, 1, 1, 1, 1, 1, 1},
                "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
            table.addRow("", "", "", "", "", "1", "2");
            table.addRow("3", "4", "5", "6", "7", "8", "9");
            table.addRow("10", "11", "12", "13", "14", "15", "16");
            document.add(table);
        });
    }

    private static byte[] ticket() {
        return create(new PageSize(420, 180), EMBER, 16, "Event ticket", document -> {
            document.add(new Paragraph("NORTHSTAR SESSIONS").bold().setFontSize(16).setFontColor(EMBER.accent()));
            document.add(new Paragraph("13 August 2026 · Door 19:00 · Seat C14"));
            document.add(new Image(new Code128Barcode("TICKET-C14")
                .withBarHeight(28f)).scaleToFit(220, 46).setMarginTop(10));
            document.add(new Image(new QrCode("https://tickets.skaldpdf.org/C14").withModuleSize(2.2f))
                .scaleToFit(56, 56).setFixedPosition(340, 40, 56));
        });
    }

    private static byte[] boardingPass() {
        return create(new PageSize(460, 200), OCEAN, 16, "Boarding pass", document -> {
            document.add(new Paragraph("OSL → ARN").bold().setFontSize(22).setFontColor(OCEAN.accent()));
            document.add(new Paragraph("DY814 · Gate 22 · Seat 12A · 06:25"));
            document.add(new Image(new Code128Barcode("DY81412A")
                .withBarHeight(30f)).scaleToFit(300, 52).setMarginTop(8));
        });
    }

    private static byte[] badge() {
        return create(new PageSize(252, 360), FOREST, 18, "Badge", document -> {
            document.add(new Div()
                .setBackground(LinearGradient.vertical(FOREST.accent(), DeviceRgb.hex("#0F2F26")))
                .setBorderRadius(16)
                .setPadding(20)
                .setHeight(280)
                .add(new Paragraph("ADA HOLM").bold().setFontSize(22).setFontColor(ColorConstants.WHITE))
                .add(new Paragraph("Staff engineer").setFontColor(ColorConstants.WHITE).setMarginTop(6)));
        });
    }

    private static byte[] businessCard() {
        return create(new PageSize(252, 144), SLATE, 14, "Business card", document -> {
            document.add(new Paragraph("SKALD").bold().setFontSize(16).setFontColor(SLATE.accent()));
            document.add(new Paragraph("Ada Holm · Documents").setFontSize(9));
            document.add(new Paragraph("ada@skaldpdf.org").setFontSize(9).setFontColor(SLATE.muted()));
        });
    }

    private static byte[] postcard() {
        return create(new PageSize(432, 288), EMBER, 20, "Postcard", document -> {
            document.add(new Paragraph("From the studio").bold().setFontSize(20));
            document.add(new Paragraph("The new invoices are quieter. Come see the proofs.").setMarginTop(8));
        });
    }

    private static byte[] flyer() {
        return create("Flyer", document -> {
            document.add(new Div()
                .setBackground(LinearGradient.vertical(FOREST.accent(), DeviceRgb.of(12, 40, 32)))
                .setBorderRadius(14)
                .setPadding(28)
                .add(new Paragraph("Open studio").bold().setFontSize(32).setFontColor(ColorConstants.WHITE))
                .add(new Paragraph("Thursday 20:00 · Grünerløkka").setFontColor(ColorConstants.WHITE).setMarginTop(8)));
        });
    }

    private static byte[] poster() {
        return create(PageSize.A3, FOREST, 48, "Poster", document -> {
            document.add(new Paragraph("SKALD").bold().setFontSize(72).setFontColor(FOREST.accent()));
            document.add(new Paragraph("Documents without the leftover decades.").setFontSize(22));
        });
    }

    private static byte[] brochure() {
        return create(PageSize.A4.landscape(), OCEAN, 36, "Brochure", document -> {
            var columns = new Table(2).useAllAvailableWidth().setBorder(Border.NO_BORDER);
            columns.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(8)
                .add(new Paragraph("For finance teams").bold().setFontSize(18))
                .add(new Paragraph(BODY).justify().setMarginTop(8)));
            columns.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(8)
                .add(new Paragraph("What you get").bold().setFontSize(18))
                .add(new Paragraph("Invoices, payslips, and statements.").setMarginTop(8)));
            document.add(columns);
        });
    }

    private static byte[] invitation() {
        return create(PageSize.A5, EMBER, 28, "Invitation", document -> {
            heading(document, EMBER, "Please come");
            document.add(new Paragraph("Dinner at the studio, 22 August, 19:00.").setTextAlignment(TextAlignment.CENTER));
        });
    }

    private static byte[] thankYou() {
        return create(PageSize.A5, FOREST, 28, "Thank you", document -> {
            heading(document, FOREST, "Thank you");
            document.add(new Paragraph("The samples landed well. We will send a revised set on Monday.")
                .setTextAlignment(TextAlignment.CENTER));
        });
    }

    private static byte[] memo() {
        return create("Memo", document -> {
            heading(document, SLATE, "Internal memo");
            document.add(new Paragraph("From Friday, all customer PDFs are written with Skald. No PDF 1.x switch remains.")
                .justify());
        });
    }

    private static byte[] letter() {
        return create("Letter", document -> {
            document.add(new Paragraph("Oslo, 13 August 2026").setTextAlignment(TextAlignment.RIGHT).setFontColor(FOREST.muted()));
            document.add(new Paragraph("Dear customer,").setMarginTop(18));
            document.add(new Paragraph(BODY).justify().setMarginTop(10));
            document.add(new Paragraph("Yours sincerely,\nAda Holm").setMarginTop(24));
        });
    }

    private static byte[] itinerary() {
        return create("Itinerary", document -> {
            heading(document, OCEAN, "Oslo–Stockholm");
            var table = styledTable(OCEAN, new float[] {2, 3, 3}, "When", "What", "Where");
            table.addRow("06:25", "Flight DY814", "OSL");
            table.addRow("10:00", "Workshop", "Södermalm");
            table.addRow("18:10", "Return DY819", "ARN");
            document.add(table);
        });
    }

    private static byte[] hotelFolio() {
        return create("Hotel folio", document -> {
            heading(document, SLATE, "Hotel folio");
            var table = styledTable(SLATE, new float[] {3, 2}, "Charge", "NOK");
            table.addRow("Room × 2", "3 180.00");
            table.addRow("Breakfast", "0.00");
            table.addRow("City tax", "78.00");
            document.add(table);
        });
    }

    private static byte[] survey() {
        return create("Survey results", document -> {
            heading(document, FOREST, "Customer survey");
            var table = styledTable(FOREST, new float[] {4, 2}, "Question", "Score");
            table.addRow("The invoice is easy to read", "4.7");
            table.addRow("The PDF opens everywhere", "4.9");
            document.add(table);
        });
    }

    private static byte[] quiz() {
        return create("Quiz results", document -> {
            heading(document, OCEAN, "PDF 2.0 quiz");
            document.add(new Paragraph("Score 9 / 10").bold().setFontSize(20).setFontColor(OCEAN.accent()));
            document.add(muted("Missed: object stream packing limits"));
        });
    }

    private static byte[] processSteps() {
        return create("Process steps", document -> {
            heading(document, SLATE, "How a document is written");
            document.add(new ListBlock(ListBlock.Marker.DECIMAL)
                .add("Consume flow elements incrementally")
                .add("Subset fonts and share images")
                .add("Pack small objects into streams")
                .add("Write a compressed xref stream"));
        });
    }

    private static byte[] orgBoxes() {
        return create("Organisation", document -> {
            heading(document, FOREST, "Document platform");
            var table = new Table(3).useAllAvailableWidth().setBorder(Border.NO_BORDER);
            for (var name : List.of("Core", "Layout", "Barcode")) {
                table.addCell(new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(FOREST.surface())
                    .setBorderRadius(10).setPadding(14)
                    .add(new Paragraph(name).bold().setTextAlignment(TextAlignment.CENTER)));
            }
            document.add(table);
        });
    }

    private static byte[] heatmap() {
        return create("Heatmap table", document -> {
            heading(document, EMBER, "Demand by weekday");
            var table = new Table(6).useAllAvailableWidth();
            table.addHeaderRow("", "Mon", "Tue", "Wed", "Thu", "Fri");
            table.addCell(body(EMBER, "Oslo"));
            table.addCell(heat(0.2f));
            table.addCell(heat(0.4f));
            table.addCell(heat(0.7f));
            table.addCell(heat(0.9f));
            table.addCell(heat(0.5f));
            document.add(table);
        });
    }

    private static Cell heat(float intensity) {
        var color = new DeviceRgb(255, (int) (243 - intensity * 140), (int) (208 - intensity * 150));
        return new Cell().setBackgroundColor(color).setBorder(new SolidBorder(ColorConstants.LINE, 0.3f))
            .add(new Paragraph(Integer.toString(Math.round(intensity * 100))).setTextAlignment(TextAlignment.CENTER));
    }

    private static byte[] watermarked() {
        return create("Preview invoice", document -> {
            document.setWatermark("PREVIEW");
            heading(document, FOREST, "Invoice preview");
            document.add(new Paragraph("This file is stamped as a preview on close."));
        });
    }

    private static byte[] gradientCover() {
        return create("Cover", document -> {
            document.add(new Div()
                .setBackground(LinearGradient.vertical(DeviceRgb.hex("#10233A"), DeviceRgb.hex("#1F4E79")))
                .setBorderRadius(18)
                .setPadding(36)
                .setHeight(320)
                .add(new Paragraph("Annual review").setFontColor(ColorConstants.WHITE).setFontSize(12))
                .add(new Paragraph("2026").bold().setFontSize(48).setFontColor(ColorConstants.WHITE).setMarginTop(8))
                .add(new Paragraph("Northstar Ledger AS").setFontColor(ColorConstants.WHITE).setMarginTop(12)));
        });
    }

    private static byte[] barcodePack() {
        return create("Barcode pack", document -> {
            heading(document, FOREST, "Barcode pack");
            document.add(new Image(new Ean13Barcode("5901234123457").withBarHeight(40f)).scaleToFit(260, 90));
            document.add(new Image(new Code128Barcode("SKALD-PACK")
                .withBarHeight(36f)).scaleToFit(280, 80).setMarginTop(16));
            document.add(new Image(new QrCode("https://skaldpdf.org").withModuleSize(2.8f))
                .scaleInto(96, 96).setMarginTop(16));
            document.add(new Image(new org.skaldpdf.barcode.Gs1128Barcode("(01)09501101530003")
                .withBarHeight(28f)).scaleInto(320, 70).setMarginTop(16));
            document.add(new Image(new org.skaldpdf.barcode.UpcABarcode("03600029145")
                .withBarHeight(28f)).scaleInto(260, 70).setMarginTop(16));
        });
    }

    private static byte[] mergedPack() {
        var first = create("Cover note", document -> heading(document, FOREST, "Cover note"));
        var second = create("Attachment", document -> heading(document, OCEAN, "Technical attachment"));
        return Pdf.merge(List.of(first, second));
    }

    private static byte[] multiPageReport() {
        return create("Multi-page report", document -> {
            document.addOutline("Summary", 1);
            document.addOutline("Detail", 2);
            heading(document, FOREST, "Operating report");
            document.add(new Paragraph("Contents").bold().setMarginTop(8));
            document.add(new Paragraph("1. Summary").setNamedDestination("summary").setFontColor(FOREST.accent()));
            document.add(new Paragraph("2. Detail").setNamedDestination("detail").setFontColor(FOREST.accent()));
            document.add(new Paragraph("Summary").bold().setLocalDestination("summary").setMarginTop(10));
            document.add(new Paragraph(BODY).justify().setMarginTop(10));
            document.add(new AreaBreak());
            document.add(new Paragraph("Detail").bold().setFontSize(20).setFontColor(FOREST.accent())
                .setLocalDestination("detail").setMarginBottom(8));
            for (int index = 1; index <= 24; index++) {
                document.add(new Paragraph("Observation " + index + ". " + BODY).setFontSize(10));
            }
        });
    }

    private static byte[] styleGuide() {
        return create("Style guide", document -> {
            heading(document, FOREST, "Skald document style");
            document.add(new Paragraph("Ink, accent, muted, and surface. Prefer 10.5–11 pt body, 1.35 leading, "
                + "and reserved running chrome.").justify());
            document.add(new Paragraph("Italic is a real embedded face, not a slanted regular.")
                .italic().setMarginTop(8));
            var swatches = new Table(4).useAllAvailableWidth().setBorder(Border.NO_BORDER).setMarginTop(16);
            for (var color : List.of(ColorConstants.INK, ColorConstants.ACCENT, ColorConstants.SURFACE, ColorConstants.LINE)) {
                swatches.addCell(new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(color)
                    .setBorderRadius(8).setHeight(36));
            }
            document.add(swatches);
        });
    }

    private static void banner(Document document, Theme theme, String eyebrow, String title) {
        document.add(new Div()
            .setBackground(LinearGradient.horizontal(theme.accent(), DeviceRgb.of(
                Math.min(255, theme.accent().redValue() + 24),
                Math.min(255, theme.accent().greenValue() + 18),
                Math.min(255, theme.accent().blueValue() + 12)))
            )
            .setBorderRadius(10)
            .setPadding(16)
            .add(new Paragraph(eyebrow).setFontSize(9).setFontColor(ColorConstants.WHITE))
            .add(new Paragraph(title).bold().setFontSize(22).setFontColor(ColorConstants.WHITE)));
    }

    private static void heading(Document document, Theme theme, String title) {
        document.add(new Paragraph(title).bold().setFontSize(20).setFontColor(theme.accent()).setMarginBottom(8));
        document.add(new LineSeparator(new SolidLine(0.8f, theme.line())).setMarginBottom(12));
    }

    private static void parties(Document document, Theme theme) {
        var table = new Table(UnitValue.createPercentArray(new float[] {1, 1})).useAllAvailableWidth()
            .setBorder(Border.NO_BORDER).setMarginTop(16);
        table.addCell(new Cell().setBorder(Border.NO_BORDER)
            .add(new Paragraph("From").setFontSize(8).setFontColor(theme.muted()))
            .add(new Paragraph("Northstar Ledger AS\nKarl Johans gate 1\n0154 Oslo").setFontSize(10)));
        table.addCell(new Cell().setBorder(Border.NO_BORDER)
            .add(new Paragraph("Bill to").setFontSize(8).setFontColor(theme.muted()))
            .add(new Paragraph("Nordlys Butikk AS\nStorgata 10\n0184 Oslo").setFontSize(10)));
        document.add(table);
    }

    private static Table styledTable(Theme theme, float[] widths, String... headers) {
        var table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
        for (var header : headers) {
            table.addHeaderCell(new Cell().add(new Paragraph(header).bold().setFontSize(8).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(theme.accent())
                .setBorder(new SolidBorder(theme.accent(), 0.4f))
                .setPadding(6));
        }
        return table;
    }

    private static Cell body(Theme theme, String text) {
        return new Cell().add(new Paragraph(text).setFontSize(9))
            .setBorder(new SolidBorder(theme.line(), 0.35f))
            .setPadding(5);
    }

    private static Paragraph muted(String text) {
        return new Paragraph(text).setFontSize(9).setFontColor(ColorConstants.MUTED).setMarginTop(8);
    }

    private static String money(int value) {
        return "%,d.00".formatted(value).replace(',', ' ');
    }

    record Theme(DeviceRgb ink, DeviceRgb accent, DeviceRgb surface, DeviceRgb line, DeviceRgb muted) {
    }

    record NamedPdf(String name, byte[] bytes) {
    }
}
