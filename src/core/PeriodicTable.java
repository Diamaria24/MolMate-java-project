package core;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class PeriodicTable {

    // --- NEW: The public 'Element' record for use by other modules ---
    public record Element(
            String name,
            String symbol,
            int atomicNumber,
            double atomicMass,
            int period,
            int group,
            String category,
            String summary
    ) {}

    private static JSONArray elements;

    // === Load JSON dataset (Bowserinator schema) ===
    public static void loadElements() {
        if (elements != null) return;
        try {
            InputStream is = PeriodicTable.class.getClassLoader().getResourceAsStream("elements_full_named.json");
            if (is == null) {
                System.out.println("❌ Failed to load JSON: elements_full_named.json not found in resources!");
                return;
            }
            JSONParser parser = new JSONParser();
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            JSONObject root = (JSONObject) parser.parse(reader);
            elements = (JSONArray) root.get("elements");
            reader.close();
            // System.out.println("✅ Loaded Periodic Table elements."); // Quieter for library use
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    // --- NEW/CORRECTED Methods for Learning Modules ---
    public static List<Element> getAllElements() {
        loadElements();
        List<Element> allElementsList = new ArrayList<>();
        if (elements == null) return allElementsList;

        for (Object obj : elements) {
            JSONObject el = (JSONObject) obj;
            Element element = new Element(
                    (String) el.get("name"),
                    (String) el.get("symbol"),
                    ((Long) el.get("number")).intValue(),
                    el.get("atomic_mass") != null ? Double.parseDouble(el.get("atomic_mass").toString()) : 0.0,
                    ((Long) el.get("period")).intValue(),
                    ((Long) el.get("group")).intValue(),
                    (String) el.get("category"),
                    (String) el.get("summary")
            );
            allElementsList.add(element);
        }
        return allElementsList;
    }

    public static Element getRandomElement() {
        loadElements();
        if (elements == null || elements.isEmpty()) {
            // Provide a complete fallback with all 8 required arguments
            return new Element("Hydrogen", "H", 1, 1.008, 1, 1, "diatomic nonmetal", "A standard chemical element.");
        }
        int randomIndex = new Random().nextInt(elements.size());
        JSONObject el = (JSONObject) elements.get(randomIndex);
        return new Element(
                (String) el.get("name"),
                (String) el.get("symbol"),
                ((Long) el.get("number")).intValue(),
                el.get("atomic_mass") != null ? Double.parseDouble(el.get("atomic_mass").toString()) : 0.0,
                ((Long) el.get("period")).intValue(),
                ((Long) el.get("group")).intValue(),
                (String) el.get("category"),
                (String) el.get("summary")
        );
    }

    // === ANSI Colors for ASCII output ===
    private static final Map<String, String> COLORS = new HashMap<>();
    private static final String RESET = "\u001B[0m";
    static {
        COLORS.put("diatomic nonmetal", "\u001B[32m");
        COLORS.put("noble gas", "\u001B[36m");
        COLORS.put("alkali metal", "\u001B[31m");
        COLORS.put("alkaline earth metal", "\u001B[33m");
        COLORS.put("metalloid", "\u001B[35m");
        COLORS.put("transition metal", "\u001B[34m");
        COLORS.put("post-transition metal", "\u001B[37m");
        COLORS.put("halogen", "\u001B[95m");
        COLORS.put("lanthanide", "\u001B[93m");
        COLORS.put("actinide", "\u001B[91m");
    }

    public static String getHexColorForCategory(String category) {
        return switch (category.toLowerCase()) {
            case "alkali metal" -> "#FFC3A0"; // Light Orange
            case "alkaline earth metal" -> "#FFD700"; // Gold
            case "transition metal" -> "#ADD8E6"; // Light Blue
            case "post-transition metal", "metal" -> "#D3D3D3"; // Light Grey
            case "metalloid" -> "#DAA520"; // Goldenrod
            case "diatomic nonmetal", "polyatomic nonmetal", "reactive nonmetal" -> "#C1E1C1"; // Light Green
            case "halogen" -> "#98FB98"; // Pale Green
            case "noble gas" -> "#DDA0DD"; // Plum
            case "lanthanide" -> "#FFB6C1"; // Light Pink
            case "actinide" -> "#D8BFD8"; // Thistle
            default -> "#FFFFFF"; // White
        };
    }

    // === Helper Methods ===
    public static String getElementName(String symbol) {
        loadElements();
        for (Object obj : elements) {
            JSONObject el = (JSONObject) obj;
            if (el.get("symbol").equals(symbol)) {
                return (String) el.get("name");
            }
        }
        return "Unknown";
    }

    public static double getMolarMass(String symbol) {
        loadElements();
        for (Object obj : elements) {
            JSONObject el = (JSONObject) obj;
            if (el.get("symbol").equals(symbol)) {
                Object mass = el.get("atomic_mass");
                if (mass instanceof Number) {
                    return ((Number) mass).doubleValue();
                }
            }
        }
        return 0.0;
    }


    // === NEW: Get phase (solid, liquid, gas, aqueous) ===
    public static String getCompoundState(String symbol) {
        loadElements();
        for (Object obj : elements) {
            JSONObject el = (JSONObject) obj;
            if (el.get("symbol").equals(symbol)) {
                Object phase = el.get("phase"); // JSON contains "phase": "Solid", "Liquid", "Gas"
                if (phase != null) {
                    return phase.toString();
                }
            }
        }
        return null;
    }

    public static String getClosestMatch(String input) {
        loadElements();
        String bestMatch = null;
        int bestDist = Integer.MAX_VALUE;
        for (Object obj : elements) {
            JSONObject el = (JSONObject) obj;
            String name = (String) el.get("name");
            int dist = levenshtein(input.toLowerCase(), name.toLowerCase());
            if (dist < bestDist && dist <= 2) {
                bestDist = dist;
                bestMatch = name;
            }
        }
        return bestMatch;
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                                dp[i - 1][j] + 1,
                                dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    // === Search Elements with Fuzzy Yes/No ===
    public static void searchElement(String query, Scanner scanner) {
        loadElements();
        JSONObject found = null;
        for (Object obj : elements) {
            JSONObject element = (JSONObject) obj;
            String name = (String) element.get("name");
            String symbol = (String) element.get("symbol");
            if (name.equalsIgnoreCase(query) || symbol.equalsIgnoreCase(query)) {
                found = element;
                break;
            }
        }

        if (found == null) {
            String suggestion = getClosestMatch(query);
            if (suggestion != null) {
                System.out.print("❌ Not found. Did you mean: " + suggestion + "? (yes/no): ");
                String choice = scanner.nextLine().trim().toLowerCase();
                if (choice.equals("yes")) {
                    searchElement(suggestion, scanner);
                }
            } else {
                System.out.println("❌ Element not found!");
            }
            return;
        }

        System.out.println("\n=== Element Details ===");
        System.out.println("Atomic Number: " + found.get("number"));
        System.out.println("Name: " + found.get("name"));
        System.out.println("Symbol: " + found.get("symbol"));
        System.out.println("Category: " + found.get("category"));
        System.out.println("Atomic Mass: " + found.get("atomic_mass"));

        String phase = (String) found.get("phase");
        System.out.println("State (Phase): " + phase);

        // ✅ Extra check for gas-phase stoichiometry
        if (phase != null && phase.equalsIgnoreCase("Gas")) {
            System.out.println("⚡ Eligible for gas-volume stoichiometry (22.4 L/mol at STP).");
        } else {
            System.out.println("❌ Not eligible for gas-volume stoichiometry (only gases use liters).");
        }

        System.out.println("Electronegativity: " + found.get("electronegativity_pauling"));
        System.out.println("Electron Configuration: " + found.get("electron_configuration"));
        System.out.println("Fun Fact: " + found.get("summary"));
    }




    // === Export Details ===
    public static void exportDetails(String elementName) {
        loadElements();
        for (Object obj : elements) {
            JSONObject element = (JSONObject) obj;
            if (element.get("name").toString().equalsIgnoreCase(elementName)) {
                try (PrintWriter writer = new PrintWriter(new FileWriter("resources/" + elementName + "_details.txt"))) {
                    writer.println("Atomic Number: " + element.get("number"));
                    writer.println("Name: " + element.get("name"));
                    writer.println("Symbol: " + element.get("symbol"));
                    writer.println("Category: " + element.get("category"));
                    writer.println("Atomic Mass: " + element.get("atomic_mass"));
                    writer.println("State (Phase): " + element.get("phase"));
                    writer.println("Electronegativity: " + element.get("electronegativity_pauling"));
                    writer.println("Electron Configuration: " + element.get("electron_configuration"));
                    writer.println("Fun Fact: " + element.get("summary"));
                    System.out.println("✅ Exported details to resources/" + elementName + "_details.txt");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }
        }
        System.out.println("❌ Element not found for export.");
    }

    // === Export ASCII Table ===
    public static void exportASCIITable() {
        loadElements();
        if (elements == null) return;

        Map<Integer, JSONObject> map = new HashMap<>();
        for (Object obj : elements) {
            JSONObject el = (JSONObject) obj;
            int num = ((Long) el.get("number")).intValue();
            map.put(num, el);
        }

        int[][] layout = new int[10][18];
        layout[0][0] = 1; layout[0][17] = 2;
        layout[1][0] = 3; layout[1][1] = 4;
        layout[1][12] = 5; layout[1][13] = 6; layout[1][14] = 7; layout[1][15] = 8; layout[1][16] = 9; layout[1][17] = 10;
        layout[2][0] = 11; layout[2][1] = 12;
        layout[2][12] = 13; layout[2][13] = 14; layout[2][14] = 15; layout[2][15] = 16; layout[2][16] = 17; layout[2][17] = 18;
        for (int i = 0; i < 18; i++) layout[3][i] = 19 + i;
        for (int i = 0; i < 18; i++) layout[4][i] = 37 + i;
        for (int i = 0; i < 18; i++) layout[5][i] = 55 + i;
        for (int i = 0; i < 14; i++) layout[7][i+2] = 57 + i;
        for (int i = 0; i < 18; i++) layout[6][i] = 87 + i;
        for (int i = 0; i < 14; i++) layout[8][i+2] = 89 + i;

        System.out.println("\n=== Periodic Table (ASCII with Colors) ===");
        for (int r = 0; r < layout.length; r++) {
            for (int c = 0; c < layout[r].length; c++) {
                int num = layout[r][c];
                if (map.containsKey(num)) {
                    JSONObject el = map.get(num);
                    String sym = (String) el.get("symbol");
                    String cat = ((String) el.get("category")).toLowerCase();
                    String color = COLORS.getOrDefault(cat, "");
                    System.out.printf("%s%-4s%s", color, sym, RESET);
                } else {
                    System.out.print("    ");
                }
            }
            if (r == 7) System.out.print("   * Ln = Lanthanides (57–71)");
            if (r == 8) System.out.print("   * Ac = Actinides (89–103)");
            System.out.println();
        }

        System.out.println("\nLegend:");
        for (Map.Entry<String, String> entry : COLORS.entrySet()) {
            System.out.println(entry.getValue() + entry.getKey() + RESET);
        }
    }

    // === Export Image Table ===
// === Export Image Table ===
    public static void exportImageTable() {
        loadElements();
        if (elements == null) return;

        int cellW = 90, cellH = 80;
        int rows = 12, cols = 18;
        BufferedImage img = new BufferedImage(cols * cellW, rows * cellH + 250, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Map<Integer, JSONObject> map = new HashMap<>();
        for (Object obj : elements) {
            JSONObject el = (JSONObject) obj;
            int num = ((Long) el.get("number")).intValue();
            map.put(num, el);
        }

        int[][] layout = new int[10][18];
        layout[0][0] = 1; layout[0][17] = 2;
        layout[1][0] = 3; layout[1][1] = 4;
        layout[1][12] = 5; layout[1][13] = 6; layout[1][14] = 7; layout[1][15] = 8; layout[1][16] = 9; layout[1][17] = 10;
        layout[2][0] = 11; layout[2][1] = 12;
        layout[2][12] = 13; layout[2][13] = 14; layout[2][14] = 15; layout[2][15] = 16; layout[2][16] = 17; layout[2][17] = 18;
        for (int i = 0; i < 18; i++) layout[3][i] = 19 + i;
        for (int i = 0; i < 18; i++) layout[4][i] = 37 + i;
        for (int i = 0; i < 18; i++) layout[5][i] = 55 + i;
        for (int i = 0; i < 14; i++) layout[7][i+2] = 57 + i;
        for (int i = 0; i < 18; i++) layout[6][i] = 87 + i;
        for (int i = 0; i < 14; i++) layout[8][i+2] = 89 + i;

        for (int r = 0; r < layout.length; r++) {
            for (int c = 0; c < layout[r].length; c++) {
                int num = layout[r][c];
                if (map.containsKey(num)) {
                    JSONObject el = map.get(num);
                    String sym = (String) el.get("symbol");
                    String name = (String) el.get("name");
                    String cat = ((String) el.get("category")).toLowerCase();

                    Color col = switch (cat) {
                        case "diatomic nonmetal" -> Color.GREEN;
                        case "noble gas" -> Color.CYAN;
                        case "alkali metal" -> Color.RED;
                        case "alkaline earth metal" -> Color.YELLOW;
                        case "metalloid" -> Color.MAGENTA;
                        case "transition metal" -> Color.BLUE;
                        case "post-transition metal" -> Color.GRAY;
                        case "halogen" -> Color.PINK;
                        case "lanthanide" -> Color.ORANGE;
                        case "actinide" -> Color.LIGHT_GRAY;
                        default -> Color.WHITE;
                    };

                    int x = c * cellW;
                    int y = r * cellH;

                    g.setColor(col);
                    g.fillRect(x, y, cellW, cellH);
                    g.setColor(Color.BLACK);
                    g.drawRect(x, y, cellW, cellH);

                    g.setFont(new Font("Arial", Font.PLAIN, 12));
                    g.drawString(String.valueOf(num), x + 5, y + 15);

                    g.setFont(new Font("Arial", Font.BOLD, 22));
                    FontMetrics fm = g.getFontMetrics();
                    int textWidth = fm.stringWidth(sym);
                    int textHeight = fm.getAscent();
                    int cx = x + (cellW - textWidth) / 2;
                    int cy = y + (cellH + textHeight) / 2 - 10;
                    g.drawString(sym, cx, cy);

                    g.setFont(new Font("Arial", Font.PLAIN, 12));
                    FontMetrics fm2 = g.getFontMetrics();
                    int nameWidth = fm2.stringWidth(name);
                    int nx = x + (cellW - nameWidth) / 2;
                    int ny = cy + 20;
                    g.drawString(name, nx, ny);
                }
            }
        }

        // Legend
        int legendY = rows * cellH + 40;
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        int x = 20;
        for (String cat : COLORS.keySet()) {
            Color col = switch (cat) {
                case "diatomic nonmetal" -> Color.GREEN;
                case "noble gas" -> Color.CYAN;
                case "alkali metal" -> Color.RED;
                case "alkaline earth metal" -> Color.YELLOW;
                case "metalloid" -> Color.MAGENTA;
                case "transition metal" -> Color.BLUE;
                case "post-transition metal" -> Color.GRAY;
                case "halogen" -> Color.PINK;
                case "lanthanide" -> Color.ORANGE;
                case "actinide" -> Color.LIGHT_GRAY;
                default -> Color.WHITE;
            };
            g.setColor(col);
            g.fillRect(x, legendY - 12, 20, 20);
            g.setColor(Color.BLACK);
            g.drawRect(x, legendY - 12, 20, 20);
            g.drawString(cat, x + 30, legendY + 5);
            legendY += 25;
        }

        g.dispose();
        try {
            File file = new File("resources/periodic_table.png");
            ImageIO.write(img, "png", file);
            System.out.println("✅ Exported periodic table image at: " + file.getAbsolutePath());

            // Auto-open image
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // === CLI ===
    public static void main(String[] args) {
        loadElements();
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Periodic Table Explorer ===");
        while (true) {
            System.out.print("\nEnter element name/symbol ('export <name>', 'table', 'image', 'exit'): ");
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("exit")) break;
            if (input.startsWith("export")) {
                String name = input.replace("export", "").trim();
                exportDetails(name);
            } else if (input.equalsIgnoreCase("table")) {
                exportASCIITable();
            } else if (input.equalsIgnoreCase("image")) {
                exportImageTable();
            } else {
                searchElement(input, scanner);
            }
        }
        scanner.close();
    }
}







