package pl.qprogramming.magicmirror.bus;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BusCreationTest {
    private static final String SCHEDULE_BASE_URL = "https://www.m.rozkladzik.pl/wroclaw/rozklad_jazdy.html?l=133&d=1&b=5&dt=-1";

    @Test
    public void loadFromHttp() {
        List<Bus.Schedule> resultSchedule = new ArrayList<>();
        try {
            Document document = Jsoup.connect(SCHEDULE_BASE_URL).get();
            Element time_table = document.getElementById("time_table");
            for (Element row : time_table.select("tr")) {
                String hourStr = row.select(".h").text();
                for (Element minute : row.select(".m")) {
                    String minuteStr = minute.text();
                    resultSchedule.add(new Bus.Schedule(hourStr, minuteStr));
                }
            }
            Bus.BusData busData = new Bus.BusData(resultSchedule);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}