package t.krekis;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SignalViewer extends JFrame {

    private JTable table;
    private DefaultTableModel model;
    private List<String[]> rawData = new ArrayList<>();

    private JComboBox<String> techFilter;
    private JTextField tacFilter;
    private JTextField searchField;

    /* ===================== CITY DATA ===================== */

    private static class City {
        String name;
        double lat;
        double lon;

        City(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private final List<City> cities = List.of(
            new City("Riga", 56.9496, 24.1052),
            new City("Daugavpils", 55.8758, 26.5358),
            new City("Liepaja", 56.5047, 21.0108),
            new City("Jelgava", 56.6511, 23.7214),
            new City("Jurmala", 56.9680, 23.7705),
            new City("Ventspils", 57.3894, 21.5646),
            new City("Rezekne", 56.5065, 27.3308),
            new City("Valmiera", 57.5385, 25.4264),
            new City("Ogre", 56.8162, 24.6146),
            new City("Jekabpils", 56.4990, 25.8572)
    );

    /* ===================== UI ===================== */

    public SignalViewer() {
        setTitle("Signal CSV Viewer");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        model = new DefaultTableModel();
        table = new JTable(model);
        JScrollPane scroll = new JScrollPane(table);

        JButton loadBtn = new JButton("Load CSV");
        loadBtn.addActionListener(e -> loadCSV());

        techFilter = new JComboBox<>(new String[]{"ALL", "LTE", "5G", "NR", "WCDMA", "GSM", "UMTS"});
        tacFilter = new JTextField(6);

        JButton filterBtn = new JButton("Filter");
        filterBtn.addActionListener(e -> applyFilters());

        searchField = new JTextField(8);
        JButton searchBtn = new JButton("Search CID");
        searchBtn.addActionListener(e -> searchConnection());

        JButton sortBtn = new JButton("Sort by Signal");
        sortBtn.addActionListener(e -> sortByStrength());

        JPanel controls = new JPanel();
        controls.add(loadBtn);
        controls.add(new JLabel("Tech:"));
        controls.add(techFilter);
        controls.add(new JLabel("LAC:"));
        controls.add(tacFilter);
        controls.add(filterBtn);
        controls.add(new JLabel("CID:"));
        controls.add(searchField);
        controls.add(searchBtn);
        controls.add(sortBtn);

        add(controls, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /* ===================== CSV LOADING ===================== */

    private void loadCSV() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File f = fc.getSelectedFile();

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {

            model.setRowCount(0);
            model.setColumnCount(0);
            rawData.clear();

            String[] headers = {
                    "Latitude", "Longitude", "Altitude", "MCC", "MNC",
                    "LAC", "CID", "Signal", "Type", "Subtype",
                    "ARFCN", "PSC", "Nearest city"
            };

            for (String h : headers) model.addColumn(h);

            String line;
            while ((line = br.readLine()) != null) {

                String[] parts = line.split(",");
                if (parts.length < 12) continue;

                double lat, lon;
                try {
                    lat = Double.parseDouble(parts[0]);
                    lon = Double.parseDouble(parts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }

                String city = nearestCity(lat, lon);

                String[] row = new String[13];
                System.arraycopy(parts, 0, row, 0, 12);
                row[12] = city;

                rawData.add(row);
                model.addRow(row);
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error loading CSV:\n" + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /* ===================== FILTERING ===================== */

    private void applyFilters() {
        String tech = (String) techFilter.getSelectedItem();
        String lac = tacFilter.getText().trim();

        model.setRowCount(0);

        for (String[] row : rawData) {
            boolean ok = true;

            if (!"ALL".equals(tech) && !row[8].equalsIgnoreCase(tech))
                ok = false;

            if (!lac.isEmpty() && !row[5].equals(lac))
                ok = false;

            if (ok) model.addRow(row);
        }
    }

    private void searchConnection() {
        String id = searchField.getText().trim();
        if (id.isEmpty()) return;

        model.setRowCount(0);

        for (String[] row : rawData) {
            if (row[6].equals(id)) {
                model.addRow(row);
            }
        }
    }

    /* ===================== SORTING ===================== */

    private void sortByStrength() {
        List<String[]> rows = new ArrayList<>();

        for (int i = 0; i < model.getRowCount(); i++) {
            String[] r = new String[model.getColumnCount()];
            for (int c = 0; c < model.getColumnCount(); c++) {
                r[c] = model.getValueAt(i, c).toString();
            }
            rows.add(r);
        }

        rows.sort((a, b) -> {
            try {
                int s1 = Integer.parseInt(a[7]);
                int s2 = Integer.parseInt(b[7]);
                return Integer.compare(s1, s2); // weaker → stronger
            } catch (NumberFormatException e) {
                return 0;
            }
        });

        model.setRowCount(0);
        for (String[] r : rows) model.addRow(r);
    }

    /* ===================== GEO ===================== */

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String nearestCity(double lat, double lon) {
        String closest = "Unknown";
        double min = Double.MAX_VALUE;

        for (City c : cities) {
            double d = haversine(lat, lon, c.lat, c.lon);
            if (d < min) {
                min = d;
                closest = c.name;
            }
        }
        return closest;
    }

    /* ===================== MAIN ===================== */

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
                new SignalViewer().setVisible(true)
        );
    }
}
