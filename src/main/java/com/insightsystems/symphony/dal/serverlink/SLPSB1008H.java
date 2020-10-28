package com.insightsystems.symphony.dal.serverlink;

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

public class SLPSB1008H extends HttpCommunicator implements Monitorable, Controller, Pingable {
    private final Map<Integer,String> outletNames = new HashMap<>();
    private int numOutlets = 8;

    public SLPSB1008H(){
        this.setAuthenticationScheme(AuthenticationScheme.Basic);
        this.setContentType("application/xml");
    }

    @Override
    protected void authenticate() throws Exception {}

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extStats = new ExtendedStatistics();
        Map<String,String> stats = new HashMap<>();
        List<AdvancedControllableProperty> controls = new ArrayList<>();

        getNumOutlets(stats);
        getSystemStats(stats);
        getOutletNames();
        createGlobalControls(stats,controls);
        getOutletStates(stats,controls);

        extStats.setStatistics(stats);
        extStats.setControllableProperties(controls);
        return Collections.singletonList(extStats);
    }

    private void getNumOutlets(Map<String, String> stats) throws Exception {
        final String response = this.doGet("/outlet.htm");
        final String outletExtract = regexFind(response,"for\\s*\\(i\\s*=\\s*10;\\s*i\\s*<\\s*(\\d+);\\s*i\\+\\+\\)");
        if (!outletExtract.isEmpty()){
            try {
                numOutlets = Integer.parseInt(outletExtract) - 10;
            } catch (Exception e){
                numOutlets = 8;
                if (this.logger.isErrorEnabled()){
                    this.logger.error("[getNumOutlets] Could not parse integer in string: " + response);
                }
            }
        } else {
            numOutlets = 8; //Default to 8
            if (this.logger.isDebugEnabled()){
                this.logger.debug("[getNumOutlets] Could not find outlet number in /outlet.htm, defaulting to 8.");
                this.logger.debug("[getNumOutlets] " + response);
            }
        }
        stats.put("System#Ports", String.valueOf(numOutlets));
    }

    private void createGlobalControls(Map<String, String> stats, List<AdvancedControllableProperty> controls) {
        AdvancedControllableProperty.Button onButton = new AdvancedControllableProperty.Button();
        onButton.setLabel("All On");
        onButton.setLabelPressed("Turning On..");
        onButton.setGracePeriod(1000L);

        AdvancedControllableProperty.Button offButton = new AdvancedControllableProperty.Button();
        offButton.setLabel("All Off");
        offButton.setLabelPressed("Turning Off..");
        offButton.setGracePeriod(1000L);

        stats.put("Outlets#_All On","0");
        stats.put("Outlets#_All Off","0");
        controls.add(new AdvancedControllableProperty("Outlets#_All On",new Date(),onButton,"0"));
        controls.add(new AdvancedControllableProperty("Outlets#_All Off",new Date(),offButton,"0"));
    }

    private void getSystemStats(Map<String, String> stats) throws Exception {
        final String response = this.doGet("/system.htm");
        stats.put("System#FirmwareVersion",regexFind(response,">Firmware\\s+Version</font>[\\s\\S]+?>([.:[-]\\w\\d]+)</font>"));
        stats.put("System#MacAddress",regexFind(response,">MAC\\s+Address</font>[\\s\\S]+?>([.:\\w\\d]+)</font>"));
    }

    private void getOutletStates(Map<String, String> stats, List<AdvancedControllableProperty> controls) throws Exception {
        final String response = regexFind(this.doPost("/status.xml",""), "<pot0>([0-9,.]*)</pot0>");
        final String[] split = response.split(",");
        stats.put("System#CurrentDraw",split[2]);
        for (int i = 0; i < numOutlets; i++) {
            stats.put(outletNames.get(i),split[i+10]);
            controls.add(createSwitch(outletNames.get(i),Integer.parseInt(split[i+10]) > 1 ? "1" : split[i+10]));
        }
    }

    private AdvancedControllableProperty createSwitch(String name, String state) {
        AdvancedControllableProperty.Switch powerSwitch = new AdvancedControllableProperty.Switch();
        powerSwitch.setLabelOff("Off");
        powerSwitch.setLabelOn("On");
        return new AdvancedControllableProperty(name,new Date(),powerSwitch,state);
    }

    private void getOutletNames() throws Exception {
        final String response = this.doPost("/Getname.xml","");
        for (int i = 0; i < 8; i++) {
            try {
                //Split comma separated list to names "Name1,Name9,Name17" etc.
                String[] xmlNames = regexFind(response, "<na" + i + ">([^<]+)</na" + i + ">?").split(",");
                updateOutletName(i, formatName(xmlNames[0], i + 1));
                updateOutletName(i + 8, formatName(xmlNames[1], i + 9));
                updateOutletName(i + (2 * 8), formatName(xmlNames[2], i + (2 * 8) + 1));
            } catch (Exception e){
                if (this.logger.isDebugEnabled()){
                    this.logger.debug("[getOutletNames] Error retrieving names " + i + "," + (i+8) + "," + (i+16) + " from response. Error: " + e.getMessage());
                }
                //Add default names
                updateOutletName(i, formatName("", i + 1));
                updateOutletName(i + 8, formatName("", i + 9));
                updateOutletName(i + (2 * 8), formatName("", i + (2 * 8) + 1));
            }

        }
    }

    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {
        if (cp == null){return;}

        char[] controlString = createControlArray(numOutlets,'0');
        final String property = cp.getProperty();
        final String value = String.valueOf(cp.getValue());

        //Handle all on/all off buttons and skip extra parsing.
        if (property.equals("_All On")) {
            this.doPost("/ons.cgi?led=" + new String(createControlArray(numOutlets, '1')), "");
            if (this.logger.isTraceEnabled())
                this.logger.trace("Posting " + this.getHost() + "/ons.cgi?led=" + new String(createControlArray(numOutlets, '1')));
            return;
        } else if (property.equals("_All Off")){
            this.doPost("/offs.cgi?led=" + new String(createControlArray(numOutlets, '1')), "");
            if (this.logger.isTraceEnabled())
                this.logger.trace("Posting " + this.getHost() + "/offs.cgi?led=" + new String(createControlArray(numOutlets, '1')));
            return;
        }

        for (int i = 0; i < outletNames.size(); i++) {
            if (outletNames.get(i).equals(property)) {
                controlString[i] = '1';
                break;
            }
        }

        //Only send control command to device if something will be controlled.
        if (!Arrays.equals(controlString, createControlArray(numOutlets,'0')))
            switch (value){
                case "false":
                case "0":
                    this.doPost("/offs.cgi?led=" + new String(controlString), "");
                    if (this.logger.isTraceEnabled())
                        this.logger.trace("Posting " + this.getHost() + "/offs.cgi?led=" + new String(controlString));
                    break;

                case "true":
                case "1":
                    this.doPost("/ons.cgi?led=" + new String(controlString), "");
                    if (this.logger.isTraceEnabled())
                        this.logger.trace("Posting " + this.getHost() + "/ons.cgi?led=" + new String(controlString));
                    break;

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
     * Performs a regex match on sourceString and returns the first group or an empty string
     * @param sourceString String to search within
     * @param regex String regex to search for in sourceString
     * @return First group match or empty string if not found
     */
    private String regexFind(String sourceString,String regex){
        final Matcher matcher = Pattern.compile(regex).matcher(sourceString);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Formats outlet name with the outlet number so outlets are ordered correctly on the Symphony portal
     * @param name Outlet name to be formatted
     * @param number Number of the outlet
     * @return Formatted name
     */
    private String formatName(String name,int number){
        String[] split = name.split(" ");
        for (int i = 0; i < split.length; i++) {
            split[i] = split[i].substring(0, 1).toUpperCase() + split[i].substring(1).toLowerCase();
        }
        return "Outlets#" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(number-1) + ": " + String.join(" ",split);
    }

    /**
     * Creates a char array used for outlet control and initialises it with a specified value
     * @param count Number of elements for the array
     * @param value Initial value for each element of the array.
     * @return
     */
    private char[] createControlArray(int count,char value){
        char[] returnArray = new char[count];
        for (int i = 0; i < count; i++){
            returnArray[i] = value;
        }
        return returnArray;
    }

    private void updateOutletName(int outlet, String name) {
        if (outletNames.containsKey(outlet)){
            outletNames.replace(outlet,name);
        }else{
            outletNames.put(outlet,name);
        }
    }

    public static void main(String[] args) throws Exception {
        SLPSB1008H pdu = new SLPSB1008H();
        pdu.setHost("***REMOVED***");
        pdu.setPort(80);
        pdu.setProtocol("http");
        pdu.setPassword("1234");
        pdu.setLogin("snmp");
        pdu.init();
        ((ExtendedStatistics)pdu.getMultipleStatistics().get(0)).getStatistics().forEach((k,v) -> System.out.println(k + " : " + v));
    }
}
