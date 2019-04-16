package com.cerner.healthe.direct.im;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.bytestreams.socks5.Socks5Proxy;
import org.nhindirect.common.tooling.Commands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.cerner.healthe.direct.im.commands.ChatCommands;
import com.cerner.healthe.direct.im.commands.FileTransferCommands;
import com.cerner.healthe.direct.im.commands.RosterCommands;

@SpringBootApplication
public class DirectImClientApplication implements CommandLineRunner
{

	@Value("${direct.im.username}")
	protected String username;
	
	@Value("${direct.im.password}")
	protected String password;
	
	@Value("${direct.im.domain}")
	protected String domain;
	
	private static boolean exitOnEndCommands = true;
	
	public static void main(String[] args) 
	{
		SpringApplication.run(DirectImClientApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception
	{
		
		/*
		 * disable the local SOCKS5 proxy in favor of a server proxy
		 * local proxies are problematic when going across networks let alone organizational boundries
		 * Smack uses the "ibb" property to indicate that only IBB should be use.  The use of SOCKS5 server proxies
		 * still imposes a security risk as it is not encrypted by default.  
		 * NOTE: The use of IBB is suppose to be used only as a fallback, however it is common denominator that meets the 
		 * security requirements out of the box.  It is also VERY slow.
		 * TODO: Looking for an alternative XEP that performs better and meets security requirements.
		 */
		Socks5Proxy.setLocalSocks5ProxyEnabled(false);
		System.setProperty("ibb", "true");
		
		final XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder().setUsernameAndPassword(username, password)
		.setXmppDomain(domain)
		//.setSecurityMode(SecurityMode.required)
		.build();
		
		final AbstractXMPPConnection con = new XMPPTCPConnection(config);
		
		System.out.println("Connecting to server for domain: " + domain);
		con.connect();
		
		System.out.println("Connection established.  Connecting as " + username + "@" + domain);
		con.login();
		
		Presence pres = new Presence(Presence.Type.available);
		pres.setStatus("Testing");
		con.sendStanza(pres);
		
		System.out.println("Login successful.  IM Client is running.");
		
		runApp(con);
		
		if (exitOnEndCommands)
		{
			pres = new Presence(Presence.Type.unavailable);
			pres.setStatus("Not testing anymore");
			con.disconnect(pres);
			System.exit(0);		
		}
	}
	
	public void runApp(AbstractXMPPConnection con)
	{
		Commands commands = new Commands("Direct IM Client");
		
		commands.register(new RosterCommands(con));
		
		commands.register(new ChatCommands(con));
		
		commands.register(new FileTransferCommands(con));
		
		commands.runInteractive();
	}

	/**
	 * Determines if the application should exit when command processing is complete.  It may be desirable to set this 
	 * to false if calling from another application context.  The default is true.
	 * @param exit True if the application should terminate on completing processing commands.  False otherwise.
	 */
	public static void setExitOnEndCommands(boolean exit)
	{
		exitOnEndCommands = exit;
	}
}
