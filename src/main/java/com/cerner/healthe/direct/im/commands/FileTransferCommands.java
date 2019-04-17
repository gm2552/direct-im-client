package com.cerner.healthe.direct.im.commands;

import java.io.File;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.filetransfer.FileTransfer;
import org.jivesoftware.smackx.filetransfer.FileTransfer.Status;
import org.jivesoftware.smackx.filetransfer.FileTransferListener;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.nhindirect.common.tooling.Command;
import org.nhindirect.common.tooling.StringArrayUtil;

public class FileTransferCommands
{
    private static final String SEND_FILE = "Sends a request to send a file." +
    		"\r\n  contact file (message) " +
            "\r\n\t contact: The username of the contact.  This is generally a full email address/Jabber id of the user." +
            "\r\n\t file: Full path of the file to send..  This should eclose in a double quote (\\\" \\\") if the file name/path contains spaces." +
    		"\r\n\t message: Optional.  A message to send to the contact.  This should eclose in a double quote (\" \") if the messge contains spaces.";
	
    private static final String ACCEPT_INCOMING_FILES = "Indicates if incoming file transfer requests should be accepted of rejected." +
    		"\r\n  accept " +
            "\r\n\t accept: Indicates if incoming requests should be accepted.  Set to true if requests should be accepted.  Any other value will set this to false"; 
    
	protected AbstractXMPPConnection con;
	
	protected FileTransferManager transferManager;
	
	protected Roster roster;
	
	protected boolean acceptFiles = true;
	
	protected ExecutorService sendFileMonitorExecutor;
	
	public FileTransferCommands(AbstractXMPPConnection con)
	{
	
		sendFileMonitorExecutor = Executors.newSingleThreadExecutor();	
		
		init(con);
	}
	
	public void init(AbstractXMPPConnection con)
	{
		this.con = con;
		
		roster = Roster.getInstanceFor(con);
		
		transferManager = FileTransferManager.getInstanceFor(con);
		
		transferManager.addFileTransferListener(new FileTransferListener() 
		{
			public void fileTransferRequest(FileTransferRequest request) 
			{
				try
				{
					handleFileTransferRequest(request);
				}
				catch (Exception e)
				{
					System.err.println("Error in incoming file transfer: " + e.getMessage());
				}
			}
		});
	}
	
	@Command(name = "SendFile", usage = SEND_FILE)
	public void sendFile(String[] args) throws Exception
	{
		final String contact = StringArrayUtil.getRequiredValue(args, 0);
		final String file = StringArrayUtil.getRequiredValue(args, 1);
		final String message = StringArrayUtil.getOptionalValue(args, 2, "");
		
		final Presence presense = roster.getPresence(JidCreate.entityBareFrom(contact));
		final Jid jid = presense.getFrom();
		if (jid == null || !(jid instanceof EntityFullJid))
		{
			System.out.println("No resource found for contact.  Contact may not be online");
			return;
		}
		
		final EntityFullJid fullJid = (EntityFullJid)jid;

		try
		{			
			final OutgoingFileTransfer transfer = transferManager.createOutgoingFileTransfer(fullJid);
			// Send the file
			transfer.sendFile(new File(file), message);
			
			System.out.println("File transfer request sent\r\n");
			
			sendFileMonitorExecutor.execute(new Runnable()
			{
				public void run()
				{
					try
					{
						monitorFileTransfer(transfer);
					}
					catch (Exception e)
					{
						System.err.println("Error in sending file " + transfer.getFileName() + ":" + e.getMessage());
					}
				}
			});
			
		}
		catch (Exception e)
		{
			System.err.println("Error sending file: " + e.getMessage());
		}
	}

	
	@Command(name = "AcceptIncomingFiles", usage = ACCEPT_INCOMING_FILES)
	public void AcceptIncomingFiles(String[] args) throws Exception
	{
		final String acceptString = StringArrayUtil.getRequiredValue(args, 0);
		
		acceptFiles = Boolean.parseBoolean(acceptString);
		
		System.out.println("Auto accept is now set to " + acceptFiles);
	}
	
	protected synchronized void handleFileTransferRequest(FileTransferRequest request) throws Exception
	{
		System.out.println("Request from " + request.getRequestor() + " to send file.");
		System.out.println("\tFile Name: " + request.getFileName());
		System.out.println("\tFile Size: " + request.getFileSize() + " bytes");
		
		if (!acceptFiles)
		{
			request.reject();
			System.out.println("\r\nFile transfer request rejected");
			return;
		}

		System.out.println("\r\nAccepting file transfer.");
		final IncomingFileTransfer transfer = request.accept();
		transfer.receiveFile(new File(request.getFileName()));
		

		monitorFileTransfer(transfer);
	}
	
	protected void monitorFileTransfer(FileTransfer transfer) throws Exception
	{
		Status currentStatus = null;
		while(!transfer.isDone()) 
		{		

			if (transfer.getStatus() != currentStatus)
			{
				currentStatus = transfer.getStatus();
				System.out.println("Transfer status: " + transfer.getStatus());
			}
			if (transfer.getStatus().equals(Status.in_progress))
			{
				final DecimalFormat df = new DecimalFormat("#.#");
				
				String percent = df.format((transfer.getProgress() * 100.0d));
				System.out.println("Transfer progress " + percent + "%");
			}

			Thread.sleep(500);
		}
		
		System.out.println("Final transfer status: " + transfer.getStatus());
		if (transfer.getStatus().equals(Status.error)) 
		{
			System.out.println("Transfer errored out with exception: " + transfer.getException().getMessage());
			transfer.getException().printStackTrace();
			return;
		} 	

	}
}
