package com.insightsystems.dal.serverlink;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.HttpCommunicator;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serverlink SLPSB1008H PDU Device Adapter
 * Company: Insight Systems
 * @author Jayden Loone (@JaydenLInsight)
 * @version
 *
 * For control of
 */
public class SLPSB1008H extends HttpCommunicator implements Monitorable, Controller, Pingable {
    public static Pattern firmwarePattern = Pattern.compile("Firmware[\\s\\r\\n]+Version[\\s\\S]+?>([\\w\\d.-]+)</font>");
    public static Pattern macAddressPattern = Pattern.compile("MAC Address[\\s\\S]+((?:[\\dA-Fa-f]{2}:){5}[\\dA-Fa-f]{2})");
    public static Pattern statusPattern = Pattern.compile("<pot0>([\\s\\S]+)</pot0>");

    public SLPSB1008H(){
        this.setAuthenticationScheme(AuthenticationScheme.Basic);
        this.setContentType("application/xml");
    }

    @Override
    protected void authenticate(){}

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extStats = new ExtendedStatistics();
        Map<String,String> stats = new HashMap<>();
        List<AdvancedControllableProperty> controls = new ArrayList<>();

        getSystemStats(stats);
        getOutletNames(stats);
        createGlobalControls(stats,controls);
        getOutletStates(stats,controls);

        extStats.setStatistics(stats);
        extStats.setControllableProperties(controls);
        return Collections.singletonList(extStats);
    }

    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {
        String property = cp.getProperty();
        String value = String.valueOf(cp.getValue());

        //Handle all on/all off buttons and skip extra parsing.
        if (property.equals("_All On")) {
            this.doPost("/ons.cgi?led=11111111", "");
            return;
        } else if (property.equals("_All Off")){
            this.doPost("/offs.cgi?led=11111111", "");
            return;
        }

        StringBuilder controlString = new StringBuilder();
        int controlIndex = property.charAt(14) - 49; //convert number ascii to array index
        for (int i=0;i < 8;i++){
            controlString.append(i == controlIndex ? "1" : "0");
        }

        switch (value){
            case "0":
                this.doPost("/offs.cgi?led=" + controlString, ""); break;
            case "1":
                this.doPost("/ons.cgi?led=" + controlString, ""); break;
            default:
                if (this.logger.isDebugEnabled()){
                    this.logger.debug("Control value: " + value + " is invalid");
                }
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        for( ControllableProperty cp : list){
            controlProperty(cp);
        }
    }

    /**
     * Create controls and statistics for global outlet controls
     * @param stats statistics Map
     * @param controls controls List
     */
    private void createGlobalControls(Map<String, String> stats, List<AdvancedControllableProperty> controls) {
        AdvancedControllableProperty.Button onButton = new AdvancedControllableProperty.Button();
        onButton.setLabel("All On");
        onButton.setLabelPressed("Turning On");
        onButton.setGracePeriod(1000L);

        AdvancedControllableProperty.Button offButton = new AdvancedControllableProperty.Button();
        offButton.setLabel("All Off");
        offButton.setLabelPressed("Turning Off");
        offButton.setGracePeriod(1000L);

        stats.put("Outlets#_All On","0");
        stats.put("Outlets#_All Off","0");
        controls.add(new AdvancedControllableProperty("Outlets#_All On",new Date(),onButton,"0"));
        controls.add(new AdvancedControllableProperty("Outlets#_All Off",new Date(),offButton,"0"));
    }

    /**
     * Retrieve PDU System statistics
     * @param stats Statistics Map
     * @throws Exception HTTP GET Exception
     */
    private void getSystemStats(Map<String, String> stats) throws Exception {
        String response = this.doGet("/system.htm");
        stats.put("System#FirmwareVersion",regexFind(response,firmwarePattern));
        stats.put("System#MacAddress",regexFind(response,macAddressPattern));
    }

    /**
     * Retrieve and parse outlet state from PDU
     * @param stats Statistics Map
     * @param controls For future feature
     * @throws Exception Http POST Exception
     */
    private void getOutletStates(Map<String, String> stats, List<AdvancedControllableProperty> controls) throws Exception {
        String response = regexFind(this.doPost("/status.xml",""), statusPattern);
        String[] split = response.split(",");
        // Current at index 2, outlets start at 10
        stats.put("System#CurrentDraw",split[2]);
        for (int i = 1; i < 9; i++) {
            stats.put("Outlets#Outlet"+ i + "State",split[i+9]);
            controls.add(createSwitch("Outlets#Outlet"+ i + "State",Integer.parseInt(split[i+9]) > 1 ? "1" : split[i+9]));
        }
    }

    /**
     * Retrieve outlet Names from device and create corresponding statistics fields
     * @param stats Statistics Map
     * @throws Exception HTTP Post Exception
     */
    private void getOutletNames(Map<String, String> stats) throws Exception {
        String response = this.doPost("/Getname.xml","").replaceAll("<[\\s\\S]+?>[\\s\\r\\n]?","");
        String[] names = response.split("[\\s\\r\\n]*?,[\\s\\r\\n]*?");
        // Array names in this order 1,8,16,2,9,17.. etc

        for (int i = 0; i < 8; i++) {
            stats.put("Outlets#Outlet" + (i+1) + "Name",names[i * 3]);
        }
    }

    /**
     * Find regex pattern within string
     * @param sourceString String to be searched
     * @param pattern Pattern to search for within string
     * @return First group in regular expression or an empty String
     */
    private String regexFind(String sourceString,Pattern pattern){
        Matcher matcher = pattern.matcher(sourceString);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Create an outlet switch control
     * @param name Name of the property to be controlled
     * @param state Current state of the control property
     * @return AdvancedControlProperty to be added to ExtendedStatistics object
     */
    private AdvancedControllableProperty createSwitch(String name, String state) {
        AdvancedControllableProperty.Switch powerSwitch = new AdvancedControllableProperty.Switch();
        powerSwitch.setLabelOff("Off");
        powerSwitch.setLabelOn("On");
        return new AdvancedControllableProperty(name,new Date(),powerSwitch,state);
    }
}
