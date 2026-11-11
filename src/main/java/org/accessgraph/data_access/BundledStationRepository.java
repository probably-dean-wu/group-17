package org.accessgraph.data_access;

import org.accessgraph.entity.Station;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads the independently verified snapshot; GTFS's parent wheelchair flag is not used as evidence. */
public final class BundledStationRepository {
    public List<Station> load() throws IOException {
        InputStream stream = getClass().getResourceAsStream("/data/ttc-stations.tsv");
        if (stream == null) { throw new IOException("The bundled TTC station catalogue is missing."); }
        List<Station> stations = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!"id\tname\tlatitude\tlongitude\taccessibility\tsource_url\tchecked_on".equals(header)) {
                throw new IOException("The bundled TTC station catalogue has an unsupported format.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split("\t", -1);
                if (values.length != 7) { throw new IOException("Invalid station catalogue row."); }
                URI source = URI.create(values[5]);
                if (!"https".equals(source.getScheme()) || !"www.ttc.ca".equals(source.getHost())
                        || !source.getPath().startsWith("/subway-stations/") || !ids.add(values[0])) {
                    throw new IOException("Invalid or duplicate station source.");
                }
                stations.add(new Station(values[0], values[1], Double.parseDouble(values[2]),
                        Double.parseDouble(values[3]), Station.Accessibility.valueOf(values[4]),
                        source, LocalDate.parse(values[6])));
            }
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new IOException("The bundled TTC station catalogue could not be read.", exception);
        }
        if (stations.isEmpty()) { throw new IOException("The bundled TTC station catalogue is empty."); }
        return List.copyOf(stations);
    }
}
