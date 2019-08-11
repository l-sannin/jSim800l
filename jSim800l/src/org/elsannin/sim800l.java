package org.elsannin;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;

import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialDataEvent;
import com.pi4j.io.serial.SerialDataEventListener;
import com.pi4j.io.serial.SerialFactory;

public class sim800l{

	private static final Logger log = LogManager.getLogger(sim800l.class);
	private static Serial serial = SerialFactory.createInstance();
	private static Queue<Byte> serialData=new LinkedList<Byte>();
	private static final byte cr = 13;
	private static final byte lf = 10;
	private static final String OK = "OK\r";
	private static final String CONNECT_OK="CONNECT OK\r";
	private static final String CLOSED = "CLOSED\r";
	private static final String SHUT_OK="SHUT OK\r";
	private static final String CLOSE_OK="CLOSE OK\r";
	//private static final String ERROR="ERROR\r";
	private static final String TIMEOUT = "TIMEOUT";
	private static final String FAILED = "FAILED";
	private static final String GOOD_SIGNAL = "GOOD_SIGNAL";
	private static final String BAD_SIGNAL = "BAD_SIGNAL";
	private static final byte ctrlz = 26;//1a
	private static final byte smsSignal = 62;//>
	private static final byte dataPrompt = 62;//>
	private static final byte esc = 27;
	private static final Pattern validIpPattern = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
	private static int defaultTimeout = 0; //5s by default

	public sim800l(String sim800SerialPort,int sim800BaudRate, int sim800DefaultTimeout) throws Exception{
		String serialPort = sim800SerialPort;
		int baudRate = sim800BaudRate;
		defaultTimeout = sim800DefaultTimeout;
		serial.open(serialPort,baudRate);
		log.log(Level.INFO,"Serial port open ->"+serialPort+":"+ baudRate);
		serial.addListener(new SerialDataEventListener() {
			public void dataReceived(SerialDataEvent event) {
				try {
					for(byte b:event.getBytes()) {
						serialData.add(b);
						Thread.sleep(1);//prevents null pointer on serialData.remove(), stranger behavior
					}
				} catch (Exception e) {
					log.log(Level.ERROR, "Error receiving data", e);
				}
			}
		});
		class keepAliveListener implements Runnable{
			@Override
			public void run() {
				while(true) {
					try {
						Thread.sleep(10);//loop to mantain thread alive
					} catch (InterruptedException e) {
						log.log(Level.ERROR, "Error on sleep process",e);
					}
				}				
			} 
	    } 
		Thread listen = new Thread(new keepAliveListener());
		listen.start();
	}


	public boolean isValidIp(String ip) {
		return validIpPattern.matcher(ip).matches();
	}

	private static void clearBuffer() {
		serialData.clear();
	}

	private String decode(String response){
		boolean start = false;
		byte lastByte = 0x00;
		long stamp = System.currentTimeMillis();
		StringBuffer buffer = new StringBuffer();
		byte b = 0;
		while((System.currentTimeMillis()-stamp)<defaultTimeout) {
			if(!serialData.isEmpty()) {
				b = serialData.remove();				
				if(start) {
					if(lastByte==cr&b==lf) {
						if(!buffer.toString().endsWith("ERROR")) {
							if(buffer.toString().endsWith((response))) {								
								log.log(Level.DEBUG, "Sim800l response -> "+buffer.toString());
								return buffer.toString();
							}
						}else {
							return FAILED;
						}
						start = false;
					}else {
						buffer.append((char)b);
					}
				}else if(lastByte==cr&b==lf) {
					start=true;
				}
				lastByte = b;				
			}
		}
		log.log(Level.WARN, "Sim800 command timeout");
		return TIMEOUT;
	}

	private String decode(String response,int timeout){
		boolean start = false;
		byte lastByte = 0x00;
		long stamp = System.currentTimeMillis();
		StringBuffer buffer = new StringBuffer();
		byte b = 0;
		while((System.currentTimeMillis()-stamp)<timeout) {
			if(!serialData.isEmpty()) {
				b = serialData.remove();				
				if(start) {
					if(lastByte==cr&b==lf) {
						if(buffer.toString().endsWith((response))) {
							log.log(Level.DEBUG, "Sim800l response -> "+buffer.toString());
							return buffer.toString();
						}
						start = false;
					}else {
						buffer.append((char)b);
					}
				}else if(lastByte==cr&b==lf) {
					start=true;
				}
				lastByte = b;				
			}
		}
		log.log(Level.WARN, "Sim800 command timeout");
		return TIMEOUT;
	}


	private boolean waitByte(byte expected){		
		long stamp = System.currentTimeMillis();
		while((System.currentTimeMillis()-stamp)<defaultTimeout) {
			if(!serialData.isEmpty()) {
				if(serialData.remove()==expected) {
					return true;
				}						
			}
		}
		log.log(Level.WARN, "Sim800 waitByte timeout");
		return false;
	}


	public boolean test() {		
		try {
			clearBuffer();
			serial.write("AT\r");
			if(decode(OK).equals(OK)) {
				return true;
			}
			return false;
		} catch (Exception e) {
			log.log(Level.ERROR, "Error on test command",e);
			return false;
		} 
	}

	public boolean disableEcho() {		
		try {
			clearBuffer();
			serial.write("ATE0\r");
			if(decode(OK).equals(OK)) {
				return true;
			}
			return false;
		} catch (Exception e) {
			log.log(Level.ERROR, "Error on disableEcho command",e);
			return false;
		} 
	}

	public String getImei() {		
		try {
			clearBuffer();
			serial.write("AT+GSN\r");
			String[] decoded = decode(OK).split("\r");
			if(decoded.length==2) {
				return decoded[0];
			}else {
				return FAILED;
			}

		} catch (Exception e) {
			log.log(Level.ERROR, "Error on getImei command",e);
			return FAILED;
		} 
	}
	//AT+CREG

	public boolean enableNetworkRegistration() {		
		try {
			clearBuffer();
			serial.write("AT+CREG=2\r");
			if(decode(OK).equals(OK)) {
				return true;
			}
			return false;			
		} catch (Exception e) {
			log.log(Level.ERROR, "Error on enableNetworkRegistration command",e);
			return false;
		} 
	}

	public boolean isNetworkRegistred() {		
		try {
			clearBuffer();
			serial.write("AT+CREG?\r");
			String[] decoded = decode(OK).split("\r");
			if(decoded.length==2) {
				//+CREG: 2,1,"6B3B","371A"
				String[] status = decoded[0].split(",");
				//System.out.println(status[1]);
				if("1".equals(status[1])) {
					log.log(Level.INFO, "lac: "+status[2]+" ci: "+status[3]);
					return true;
				}				
			}
			return false;

		} catch (Exception e) {
			log.log(Level.ERROR, "Error on isNetworkRegistred command",e);
			return false;
		} 
	}

	public boolean disableNetworkRegistrationReport() {		
		try {
			clearBuffer();
			serial.write("AT+CREG=0\r");
			if(decode(OK).equals(OK)) {
				return true;
			}
			return false;

		} catch (Exception e) {
			log.log(Level.ERROR, "Error on isNetworkRegistred command",e);
			return false;
		} 
	}


	public boolean configureApn (String apn,String user,String password) {		
		try {
			clearBuffer();
			serial.write("AT+CSTT=\"internet.tigo.bo\",\"\",\"\"\r");
			if(decode(OK).equals(OK)) {
				return true;
			}
			return false;			
		} catch (Exception e) {
			log.log(Level.ERROR, "Error on configureApn command",e);
			return false;
		} 
	}

	public boolean startGprs () {		
		try {
			clearBuffer();
			serial.write("AT+CIICR\r");
			if(decode(OK).equals(OK)) {
				return true;
			}
			return false;			
		} catch (Exception e) {
			log.log(Level.ERROR, "Error on startGprs command",e);
			return false;
		} 
	}

	public boolean setFullFunctionality () {		
		try {
			clearBuffer();
			serial.write("AT+CFUN=1\r");
			if(decode(OK).equals(OK)) {
				return true;
			}
			return false;			
		} catch (Exception e) {
			log.log(Level.ERROR, "Error on setFullFunctionality command",e);
			return false;
		} 
	}

	public String getOperator() {		
		try {
			clearBuffer();
			serial.write("AT+COPS?\r");
			String[] decoded = decode(OK).split("\r");
			if(decoded.length==2) {
				//+COPS: 0,0,"73603"
				String[] status = decoded[0].split(",");
				return status[2];								
			}
			return FAILED;

		} catch (Exception e) {
			log.log(Level.ERROR, "Error on getOperator command",e);
			return FAILED;
		} 
	}

	/*
	 * clearBuffer();
			serial.write("AT+CIPSHUT\r");
			if(decode(OK).equals(OK)) {

			}*/

	public boolean closeConnection () {		
		try {
			clearBuffer();
			serial.write("AT+CIPCLOSE\r");
			if(decode(CLOSE_OK).equals(CLOSE_OK)) {
				return true;
			}
			return false;			
		} catch (Exception e) {
			log.log(Level.ERROR, "Error on deactivateGprs command",e);
			return false;
		} 
	}
	//AT+CIPCLOSE
	public boolean deactivateGprs () {		
		try {
			clearBuffer();
			serial.write("AT+CIPSHUT\r");
			if(decode(SHUT_OK).equals(SHUT_OK)) {
				return true;
			}
			return false;			
		} catch (Exception e) {
			log.log(Level.ERROR, "Error on deactivateGprs command",e);
			return false;
		} 
	}

	public boolean connectToServer(String server, int port){
		try {			
			clearBuffer();
			serial.write("AT+CIPSTART=\"TCP\",\""+server+"\",\"8081\"\r");
			String decoded = decode(CONNECT_OK,30000);
			if(!TIMEOUT.equals(decoded)) {
				clearBuffer();
				String rawRequest="GET /hello HTTP/1.1\r\n" + 
						"Host: 181.177.142.70:8081\r\n" + 
						"Connection: keep-alive\r\n" + 
						"Upgrade-Insecure-Requests: 1\r\n" + 
						"User-Agent: Mozilla/5.0 (Linux; Android 9; SNE-LX3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.143 Mobile Safari/537.36\r\n" + 
						"Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3\r\n" + 
						"Accept-Encoding: gzip, deflate\r\n" + 
						"Accept-Language: es-US,es-419;q=0.9,es;q=0.8\r\n" + 
						"\r\n" + 
						"";
				serial.write("AT+CIPSEND="+rawRequest.length()+"\r");

				if(waitByte(dataPrompt)) {
					serial.write(rawRequest);
					while(true) {
						if(!serialData.isEmpty()) {
							System.out.print((char) (byte)serialData.remove());							
						}

					}
				}

			}

			return false;

		} catch (Exception e) {
			log.log(Level.ERROR, "Error on getOperator command",e);
			return false;
		} 
	}


	public String get(String server, int port, String resource) {		
		try {			
			clearBuffer();
			serial.write("AT+CIPSTART=\"TCP\",\""+server+"\",\"8081\"\r");
			String decoded = decode(CONNECT_OK,30000);
			if(!TIMEOUT.equals(decoded)) {
				clearBuffer();
				String rawRequest="GET "+resource+" HTTP/1.1\r\n" + 
						"Host: 181.177.142.70:8081\r\n" + 
						"Connection: close\r\n" + 
						"Upgrade-Insecure-Requests: 1\r\n" + 
						"User-Agent: elSannin (Linux; Android 9; SNE-LX3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.143 Mobile Safari/537.36\r\n" + 
						"Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3\r\n" + 
						"Accept-Encoding: gzip, deflate\r\n" + 
						"Accept-Language: es-US,es-419;q=0.9,es;q=0.8\r\n" + 
						"\r\n" + 
						"";
				serial.write("AT+CIPSEND="+rawRequest.length()+"\r");

				if(waitByte(dataPrompt)) {
					serial.write(rawRequest);
					long stamp = System.currentTimeMillis();
					StringBuilder response = new StringBuilder();
					while((System.currentTimeMillis()-stamp)<defaultTimeout){
						if(!serialData.isEmpty()) {
							response.append((char) (byte)serialData.remove());
							if(response.toString().endsWith(CLOSED)) {
								return response.toString();
							}
						}
						
					}
					return TIMEOUT;
				}

			}

			return FAILED;

		} catch (Exception e) {
			log.log(Level.ERROR, "Error on getOperator command",e);
			return FAILED;
		} 
	}

	public String getSignalQuality() {		
		try {
			clearBuffer();
			serial.write("AT+CSQ\r");
			String[] decoded = decode(OK).split("\r");
			if(decoded.length==2) {
				//+CSQ: 20,0
				String[] status = decoded[0].trim().split(":");
				String[] signals = status[1].split(",");
				float rssi = Float.parseFloat(signals[0]);
				if(rssi>=16) {
					return GOOD_SIGNAL; 
				}else {
					return BAD_SIGNAL; 
				}										
			}
			return FAILED;

		} catch (Exception e) {
			log.log(Level.ERROR, "Error on getOperator command",e);
			return FAILED;
		} 
	}


	public boolean setSmsTextMode() {		
		try {
			clearBuffer();
			serial.write("AT+CMGF=1\r");
			if(decode(OK).equals(OK)) {
				return true;
			}
			return false;			
		} catch (Exception e) {
			log.log(Level.ERROR, "Error on setSmsTextMode command",e);
			return false;
		} 
	}

	public boolean getIp() {		
		try {
			clearBuffer();
			serial.write("AT+CIFSR\r");
			String ip = decode("\r");
			if(isValidIp(ip.trim())) {
				return true;
			}
			return false;			
		} catch (Exception e) {
			log.log(Level.ERROR, "Error on setSmsTextMode command",e);
			return false;
		} 
	}

	public boolean sendSms(String msisdn,String message) {		
		try {
			clearBuffer();
			serial.write("AT+CMGS=\""+msisdn+"\"\r");
			if(waitByte(smsSignal)) {
				serial.write(message);
				serial.write(ctrlz);
				if(!TIMEOUT.equals(decode(OK))){
					return true;
				}
			}else {
				serial.write(esc);
				serial.write("\r");
			}
			return false;

		} catch (Exception e) {
			log.log(Level.ERROR, "Error on getOperator command",e);
			return false;
		} 
	}
}
