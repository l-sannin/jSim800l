package org.elsannin;

public class test {

	public static void main(String[] args) {
		sim800l sim800;
		try {
			if(args.length==8) {
				String port = args[1];
				int baudRate = Integer.parseInt(args[2]);
				int timeout = Integer.parseInt(args[3]);
				String apn= args[4];
				String server= args[5];
				int serverPort=Integer.parseInt(args[6]);
				String resource = args[7];
				sim800 = new sim800l(port,baudRate,timeout);
				System.out.println("Test: "+sim800.test());
				System.out.println("Disable Echo: "+sim800.disableEcho());
				System.out.println("IMEI: "+sim800.getImei());
				System.out.println("NetworkEnabled: "+sim800.enableNetworkRegistration());
				System.out.println("Registered: "+sim800.isNetworkRegistred());
				System.out.println("NetworkOperator: "+sim800.getOperator());
				System.out.println("Signal: "+sim800.getSignalQuality());
				System.out.println("Set sms text mode: "+sim800.setSmsTextMode());
				//System.out.println("Send sms: "+Sim800l.sendSms("msisdn", "Sms using jSim800l"));
				System.out.println("deactivateGprs: "+sim800.deactivateGprs());
				System.out.println("fullFunctionality: "+sim800.setFullFunctionality());
				System.out.println("configureApn: "+sim800.configureApn(apn, "", ""));
				System.out.println("startGprs: "+sim800.startGprs());
				System.out.println("getIp: "+sim800.getIp());
				System.out.println("get: "+sim800.get(server, serverPort, resource));
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
