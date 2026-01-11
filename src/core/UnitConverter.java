package core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class UnitConverter {

    // Conversion factors (to a consistent base unit within each category)
    private static final Map<String, Double> FACTORS = new HashMap<>();
    private static final double AVOGADRO_CONSTANT = 6.02214076e23;

    static {
        // Mass -> base unit: grams (g)
        FACTORS.put("g", 1.0);
        FACTORS.put("kg", 1000.0);
        FACTORS.put("mg", 0.001);
        FACTORS.put("lb", 453.592);   // Pound
        FACTORS.put("oz", 28.3495);   // Ounce

        // Volume -> base unit: liters (L)
        FACTORS.put("L", 1.0);
        FACTORS.put("mL", 0.001);
        FACTORS.put("m3", 1000.0);    // Cubic Meter
        FACTORS.put("gal", 3.78541);  // Gallon (US)
        FACTORS.put("qt", 0.946353);  // Quart (US)

        // Pressure -> base unit: pascals (Pa)
        FACTORS.put("Pa", 1.0);
        FACTORS.put("kPa", 1000.0);
        FACTORS.put("atm", 101325.0);
        FACTORS.put("bar", 100000.0);
        FACTORS.put("psi", 6894.76);   // Pounds per Square Inch
        FACTORS.put("torr", 133.322);  // Torr
        FACTORS.put("mmHg", 133.322);  // Millimeters of Mercury

        // Energy -> base unit: joules (J)
        FACTORS.put("J", 1.0);
        FACTORS.put("kJ", 1000.0);
        FACTORS.put("cal", 4.184);

        // Amount of substance -> base unit: moles (mol)
        FACTORS.put("mol", 1.0);
        FACTORS.put("mmol", 0.001);

        // Temperature and Particle counts are handled as special cases
    }

    // === Conversion function ===
    public static double convert(String from, String to, double value) {
        if (from.equalsIgnoreCase(to)) return value;

        // --- Special Case: Temperature conversions (non-factor based) ---
        if (from.equalsIgnoreCase("C") && to.equalsIgnoreCase("K")) return value + 273.15;
        if (from.equalsIgnoreCase("K") && to.equalsIgnoreCase("C")) return value - 273.15;
        if (from.equalsIgnoreCase("C") && to.equalsIgnoreCase("F")) return (value * 9.0 / 5.0) + 32;
        if (from.equalsIgnoreCase("F") && to.equalsIgnoreCase("C")) return (value - 32) * 5.0 / 9.0;
        if (from.equalsIgnoreCase("K") && to.equalsIgnoreCase("F")) return (value - 273.15) * 9.0 / 5.0 + 32;
        if (from.equalsIgnoreCase("F") && to.equalsIgnoreCase("K")) return (value - 32) * 5.0 / 9.0 + 273.15;

        // --- Special Case: Moles to Particles (using Avogadro's constant) ---
        String[] particleUnits = {"particles", "atoms", "molecules"};
        boolean fromIsParticle = Arrays.asList(particleUnits).contains(from.toLowerCase());
        boolean toIsParticle = Arrays.asList(particleUnits).contains(to.toLowerCase());

        if (from.equalsIgnoreCase("mol") && toIsParticle) return value * AVOGADRO_CONSTANT;
        if (fromIsParticle && to.equalsIgnoreCase("mol")) return value / AVOGADRO_CONSTANT;

        // --- General conversions using factors ---
        String fromLower = from.toLowerCase();
        String toLower = to.toLowerCase();

        if (FACTORS.containsKey(fromLower) && FACTORS.containsKey(toLower)) {
            double base = value * FACTORS.get(fromLower); // convert to the category's base unit
            return base / FACTORS.get(toLower);        // convert from base unit to the target unit
        }

        throw new IllegalArgumentException("Unsupported conversion: " + from + " -> " + to);
    }

    // === CLI interface ===
    public static void startCLI(Scanner sc) {
        System.out.println("\n=== Unit Converter ===");
        System.out.println("Enter conversion like: '150 g to oz', '72 F to C', '0.05 mol to atoms'. Type 'exit' to return.");

        while (true) {
            System.out.print("\nInput: ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;
            if (input.isEmpty()) continue;

            try {
                String[] parts = input.split(" ");
                if (parts.length != 4 || !parts[2].equalsIgnoreCase("to")) {
                    System.out.println("❌ Invalid format. Use: <value> <from_unit> to <to_unit>");
                    continue;
                }

                double value = Double.parseDouble(parts[0]);
                String from = parts[1];
                String to = parts[3];

                double result = convert(from, to, value);

                // Use scientific notation for very large or small numbers
                if (Math.abs(result) > 1e5 || Math.abs(result) < 1e-3 && result != 0) {
                    System.out.printf("✅ %.4f %s = %.4e %s%n", value, from, result, to);
                } else {
                    System.out.printf("✅ %.4f %s = %.4f %s%n", value, from, result, to);
                }

            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
            }
        }
    }
}


