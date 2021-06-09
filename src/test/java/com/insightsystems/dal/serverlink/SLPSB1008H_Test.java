package com.insightsystems.dal.serverlink;

import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.util.Map;

public class SLPSB1008H_Test {
    SLPSB1008H pdu;

    @Before
    public void setup() throws Exception {
        pdu = new SLPSB1008H();
        pdu.setHost("10.164.69.10");
        pdu.setPort(80);
        pdu.setProtocol("http");
        pdu.setPassword("1234");
        pdu.setLogin("snmp");
        pdu.init();
    }

    @Test
    public void testNamesAndStatesArePopulated() throws Exception {
        Map<String,String> stats = ((ExtendedStatistics)pdu.getMultipleStatistics().get(0)).getStatistics();
        for (int i=1;i<=8;i++){
            Assert.assertNotNull(stats.get("Outlets#Outlet"+i+"State"));
            Assert.assertNotNull(stats.get("Outlets#Outlet"+i+"Name"));
        }
        Assert.assertNotNull(stats.get("Outlets#_All Off"));
        Assert.assertNotNull(stats.get("Outlets#_All On"));
    }

    @Test
    public void testSystemStatisticsAreValid() throws Exception {
        Map<String,String> stats = ((ExtendedStatistics)pdu.getMultipleStatistics().get(0)).getStatistics();
        Assert.assertTrue(Float.parseFloat(stats.get("System#CurrentDraw")) >= 0);
        Assert.assertFalse(stats.get("System#FirmwareVersion").isEmpty());
        Assert.assertFalse(stats.get("System#MacAddress").isEmpty());
    }

    @Test
    public void testOutletControl() throws Exception {
        int outlet = 6;
        Map<String,String> stats = ((ExtendedStatistics)pdu.getMultipleStatistics().get(0)).getStatistics();
        Assert.assertEquals(stats.get("Outlets#Outlet"+outlet+"State"),"1");

        ControllableProperty cp = new ControllableProperty();
        cp.setProperty("Outlets#Outlet"+outlet+"State");
        cp.setValue(0);

        pdu.controlProperty(cp);

        Thread.sleep(10_000);

        stats = ((ExtendedStatistics)pdu.getMultipleStatistics().get(0)).getStatistics();
        Assert.assertEquals(stats.get("Outlets#Outlet"+outlet+"State"),"0");

        cp.setValue("1");
        pdu.controlProperty(cp);

        Thread.sleep(10_000);

        stats = ((ExtendedStatistics)pdu.getMultipleStatistics().get(0)).getStatistics();
        Assert.assertEquals(stats.get("Outlets#Outlet"+outlet+"State"),"1");
    }
}
