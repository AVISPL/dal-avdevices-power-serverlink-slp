package com.insightsystems.symphony.dal.serverlink;

import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class SLPSB1008H_Test {
    SLPSB1008H pdu = new SLPSB1008H();

    @Before
    public void setup() throws Exception {
        pdu.setHost("10.164.69.10");
        pdu.setPort(80);
        pdu.setProtocol("http");
        pdu.setPassword("1234");
        pdu.setLogin("snmp");
        pdu.init();
    }

    @Test
    public void checkExtendedStatistics() throws Exception {
        Map<String,String> stats = ((ExtendedStatistics) pdu.getMultipleStatistics().get(0)).getStatistics();

        Assert.assertEquals("1",stats.get("Outlets#A: Uc Flex"));
        Assert.assertEquals("1",stats.get("Outlets#B: Scaler"));
        Assert.assertEquals("1",stats.get("Outlets#C: Am300"));
        Assert.assertEquals("1",stats.get("Outlets#D: Dsp"));
        Assert.assertEquals("1",stats.get("Outlets#E: Camera"));
        Assert.assertEquals("1",stats.get("Outlets#F: Pir"));
        Assert.assertEquals("1",stats.get("Outlets#G: Amp"));
        Assert.assertEquals("1",stats.get("Outlets#H: Pc"));

        Assert.assertEquals("0",stats.get("Outlets#_All On"));
        Assert.assertEquals("0",stats.get("Outlets#_All Off"));
        Assert.assertEquals("8",stats.get("System#Ports"));
        Assert.assertNotEquals("",stats.get("System#CurrentDraw"));

        Assert.assertEquals("s4.82-091012-1cb08s",stats.get("System#FirmwareVersion"));
        Assert.assertEquals("00:06:18:75:C9:CA",stats.get("System#MacAddress"));
    }

    @Test
    public void testPortControl() throws Exception {
        Map<String,String> stats = ((ExtendedStatistics) pdu.getMultipleStatistics().get(0)).getStatistics();

        Assert.assertEquals("1",stats.get("Outlets#G: Amp"));

        ControllableProperty cp = new ControllableProperty();
        cp.setProperty("Outlets#G: Amp");
        cp.setValue("0");
        pdu.controlProperty(cp);

        Thread.sleep(10_000);

        stats = ((ExtendedStatistics) pdu.getMultipleStatistics().get(0)).getStatistics();
        Assert.assertEquals("0",stats.get("Outlets#G: Amp"));

        cp.setValue("1");
        pdu.controlProperty(cp);

        Thread.sleep(10_000);

        stats = ((ExtendedStatistics) pdu.getMultipleStatistics().get(0)).getStatistics();
        Assert.assertEquals("1",stats.get("Outlets#G: Amp"));
    }
}
