package core;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.math3.fraction.Fraction;
import org.apache.commons.math3.linear.*;

public class EquationBalancer {

    private static final String REPORT_FILE = "composition_report.csv";

    // === The original balance method now calls the new, more powerful version ===
    // This ensures that when called normally, it is NOT silent.
    public static String balance(String equation) {
        return balance(equation, false); // Default to non-silent mode
    }

    /**
     * --- NEW: Overloaded balance method with a silent mode flag ---
     * This is the main balancing engine.
     * @param equation The chemical equation to balance.
     * @param isSilent If true, no step-by-step output will be printed to the console.
     * @return The balanced equation string.
     */
    public static String balance(String equation, boolean isSilent) {
        if (!isSilent) {
            System.out.println("🔹 Input: " + equation);
        }

        String[] sides = equation.split("->");
        if (sides.length != 2) throw new IllegalArgumentException("Equation must contain '->'");

        String[] leftTermsRaw = sides[0].trim().split("\\+");
        String[] rightTermsRaw = sides[1].trim().split("\\+");
        List<String> leftTermsClean = new ArrayList<>();
        for (String term : leftTermsRaw) {
            leftTermsClean.add(term.trim().replaceAll("^[0-9]+\\s", ""));
        }
        List<String> rightTermsClean = new ArrayList<>();
        for (String term : rightTermsRaw) {
            rightTermsClean.add(term.trim().replaceAll("^[0-9]+\\s", ""));
        }

        String[] left = leftTermsClean.toArray(new String[0]);
        String[] right = rightTermsClean.toArray(new String[0]);

        List<String> compounds = new ArrayList<>(Arrays.asList(left));
        compounds.addAll(Arrays.asList(right));
        Set<String> elementSet = new LinkedHashSet<>();
        List<Map<String, Integer>> parsed = new ArrayList<>();
        for (String compound : compounds) {
            Map<String, Integer> map = parseCompound(compound);
            parsed.add(map);
            elementSet.addAll(map.keySet());
        }
        List<String> elements = new ArrayList<>(elementSet);
        int rows = elements.size(), cols = compounds.size();
        if (rows == 0 || cols == 0) throw new IllegalArgumentException("Invalid equation input.");
        double[][] matrix = new double[rows][cols];
        for (int c = 0; c < cols; c++) {
            Map<String, Integer> compoundMap = parsed.get(c);
            for (int r = 0; r < rows; r++) {
                String element = elements.get(r);
                int count = compoundMap.getOrDefault(element, 0);
                matrix[r][c] = (c < left.length) ? count : -count;
            }
        }

        // All console output is now conditional based on the 'isSilent' flag.
        if (!isSilent) {
            System.out.println("🔹 Elements detected: " + elements);
            System.out.println("🔹 Constructed Matrix [rows=elements, cols=compounds]");
            for (int r = 0; r < rows; r++) {
                System.out.printf("%-2s | ", elements.get(r));
                for (int c = 0; c < cols; c++) System.out.printf("%6.1f ", matrix[r][c]);
                System.out.println();
            }
        }

        int[] coeffs = solveBySolver(matrix);

        if (!isSilent) {
            System.out.println("🔹 Final integer coefficients = " + Arrays.toString(coeffs));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < left.length; i++) {
            if (i < coeffs.length && coeffs[i] != 0) {
                sb.append(coeffs[i] > 1 ? coeffs[i] + " " : "").append(left[i]);
                if (i < left.length - 1) sb.append(" + ");
            }
        }
        sb.append(" -> ");
        for (int i = 0; i < right.length; i++) {
            int coeffIndex = left.length + i;
            if (coeffIndex < coeffs.length && coeffs[coeffIndex] != 0) {
                sb.append(coeffs[coeffIndex] > 1 ? coeffs[coeffIndex] + " " : "").append(right[i]);
                if (i < right.length - 1) sb.append(" + ");
            }
        }
        String balancedEquation = sb.toString();

        exportResult(equation, balancedEquation, coeffs, isSilent);
        return balancedEquation;
    }

    // Export method is also updated to respect the silent flag
    private static void exportResult(String input, String balanced, int[] coeffs, boolean isSilent) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        boolean fileExists = new File(REPORT_FILE).exists();
        try (PrintWriter pw = new PrintWriter(new FileWriter(REPORT_FILE, true))) {
            if (!fileExists) pw.println("Timestamp,Type,Input,Balanced,Coefficients");
            pw.printf("%s,Equation Balance,\"%s\",\"%s\",\"%s\"%n", ts, input, balanced, Arrays.toString(coeffs));
        } catch (Exception e) {
            System.out.println("❌ Failed to write CSV: " + e.getMessage());
        }

        if (!isSilent) {
            System.out.println("📂 Result added to " + REPORT_FILE);
        }
    }

    // === ENHANCED PARSER with Parentheses Support ===
    public static Map<String, Integer> parseCompound(String compound) {
        Stack<Map<String, Integer>> stack = new Stack<>();
        stack.push(new LinkedHashMap<>());
        int i = 0;
        while (i < compound.length()) {
            char c = compound.charAt(i);
            if (Character.isUpperCase(c)) {
                int start = i++;
                while (i < compound.length() && Character.isLowerCase(compound.charAt(i))) i++;
                String element = compound.substring(start, i);
                start = i;
                while (i < compound.length() && Character.isDigit(compound.charAt(i))) i++;
                int count = (i > start) ? Integer.parseInt(compound.substring(start, i)) : 1;
                Map<String, Integer> currentMap = stack.peek();
                currentMap.put(element, currentMap.getOrDefault(element, 0) + count);
            } else if (c == '(') {
                stack.push(new LinkedHashMap<>());
                i++;
            } else if (c == ')') {
                i++;
                int start = i;
                while (i < compound.length() && Character.isDigit(compound.charAt(i))) i++;
                int multiplier = (i > start) ? Integer.parseInt(compound.substring(start, i)) : 1;
                if (stack.size() > 1) {
                    Map<String, Integer> subGroupMap = stack.pop();
                    Map<String, Integer> parentMap = stack.peek();
                    for (Map.Entry<String, Integer> entry : subGroupMap.entrySet()) {
                        parentMap.put(entry.getKey(), parentMap.getOrDefault(entry.getKey(), 0) + entry.getValue() * multiplier);
                    }
                }
            } else {
                i++;
            }
        }
        return stack.pop();
    }

    // === ROBUST SOLVER using QRDecomposition ===
    private static int[] solveBySolver(double[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        if (rows < cols - 1) {
            throw new IllegalArgumentException("Cannot balance: The equation has too many compounds for the number of elements.");
        }
        try {
            RealMatrix aPrime = new Array2DRowRealMatrix(matrix).getSubMatrix(0, rows - 1, 0, cols - 2);
            double[] c = new double[rows];
            for (int i = 0; i < rows; i++) { c[i] = -matrix[i][cols - 1]; }
            RealVector b = new ArrayRealVector(c);
            DecompositionSolver solver = new QRDecomposition(aPrime).getSolver();
            RealVector solution = solver.solve(b);
            System.out.println("🔹 Solver solution (last coeff = 1): " + solution);
            Fraction[] fracs = new Fraction[cols];
            for (int i = 0; i < cols - 1; i++) { fracs[i] = new Fraction(solution.getEntry(i), 1e-9, 1000); }
            fracs[cols - 1] = new Fraction(1);
            int lcm = 1;
            for (Fraction f : fracs) lcm = lcm(lcm, f.getDenominator());
            int[] coeffs = new int[cols];
            for (int i = 0; i < cols; i++) coeffs[i] = Math.abs(fracs[i].multiply(lcm).intValue());
            int gcd = 0;
            for (int val : coeffs) if (val != 0) gcd = (gcd == 0) ? val : gcd(gcd, val);
            if (gcd > 1) for (int i = 0; i < coeffs.length; i++) coeffs[i] /= gcd;
            return coeffs;
        } catch (Exception e) {
            System.out.println("❌ Error during algebraic solution: " + e.getMessage());
            int[] fallback = new int[cols];
            Arrays.fill(fallback, 1);
            return fallback;
        }
    }

    // === Helpers ===
    private static int gcd(int a, int b) { a = Math.abs(a); b = Math.abs(b); while (b != 0) { int temp = b; b = a % b; a = temp; } return a; }
    private static int lcm(int a, int b) { if (a == 0 || b == 0) return 1; return Math.abs(a * b) / gcd(a, b); }

    // Unchanged Methods
    public static Map<String, Integer> parseCoefficients(String balancedEq) {
        Map<String, Integer> coeffs = new LinkedHashMap<>();
        String[] sides = balancedEq.split("->");
        String[] compounds = (sides[0] + "+" + sides[1]).split("\\+");
        for (String term : compounds) {
            term = term.trim();
            if (term.isEmpty()) continue;
            String[] parts = term.split(" ");
            if (parts.length == 2) {
                coeffs.put(parts[1], Integer.parseInt(parts[0]));
            } else {
                coeffs.put(parts[0], 1);
            }
        }
        return coeffs;
    }

    public static void startCLI(Scanner sc) {
        System.out.println("\n=== Equation Balancer ===");
        System.out.println("Enter an unbalanced equation. Type 'exit' to return.");
        while (true) {
            System.out.print("Equation: ");
            String line = sc.nextLine().trim();
            if (line.equalsIgnoreCase("exit")) return;
            if (line.isEmpty()) continue;
            try {
                String balanced = balance(line);
                System.out.println("✅ Balanced Equation: " + balanced);
                System.out.println("--------------------------------------------------");
            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
            }
        }
    }
}














