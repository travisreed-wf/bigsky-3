package bigsky.messaging;

import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.Arrays;

import javax.swing.JScrollPane;
import javax.swing.text.BadLocationException;

import bigsky.BlueTextRequest;
import bigsky.BlueTextRequest.REQUEST;
import bigsky.BlueTextResponse;
import bigsky.Contact;
import bigsky.Global;
import bigsky.TaskBar;
import bigsky.gui.Conversation;
import bigsky.gui.Notification;
import bigsky.gui.SmallChat;

public class TextMessageManager extends Thread
{
	public static boolean sendTexts = true;
	
	public void run()
	{
		boolean matchR = false;
		String phoneHLine;
		try {
			synchronized(this){
				while(true){
					this.wait();					
					
					//Adding chat history from phone
					if(!Global.phoneTextHistory.isEmpty()){
						int smallChatNum = 0;
						int size = Global.phoneTextHistory.size();
						for(int i=0; i < TaskBar.smallChatWindows.size(); i++){
							if(Global.blueTextRqContact.getPhoneNumber().equals(TaskBar.smallChatWindows.get(i).getFromContact().getPhoneNumber())){
								smallChatNum = i;
								break;
							}
						}
						if(smallChatNum == 0){
							TaskBar.smallChatWindows.add(new SmallChat(new Contact("Jonathan", "Mielke", "6185204620", ""), Global.blueTextRqContact));
							smallChatNum = TaskBar.smallChatWindows.size() - 1;
						}
						
						sendTexts = false;
												
						for(int i = 0; i < size;i++){
							phoneHLine = Global.phoneTextHistory.get(Global.phoneTextHistory.size()-1).getSender().getFirstName() + ":  " + Global.phoneTextHistory.get(Global.phoneTextHistory.size()-1).getContent();
							try {
								Conversation.updateConv(Global.phoneTextHistory.get(Global.phoneTextHistory.size()-1));
								TaskBar.smallChatWindows.get(smallChatNum).receivedText(Global.phoneTextHistory.get(Global.phoneTextHistory.size()-1));
							} catch (BadLocationException e) {
								e.printStackTrace();
								System.out.println("Updating in chat history -FAILED");
							}
							
							Global.phoneTextHistory.remove(Global.phoneTextHistory.size()-1);
						}
						sendTexts = true;
					}
					
					//For updating conversations in small chat and main conversation
					if(!TaskBar.myTextArray.isEmpty()){
						System.out.println("hit manager sending");
						Notification notify = new Notification(TaskBar.myTextArray.get(0));
						matchR = false;
						for(int i=0; i < TaskBar.smallChatWindows.size(); i++){
							if(TaskBar.myTextArray.get(0).getSender().getPhoneNumber().equals(TaskBar.smallChatWindows.get(i).getFromContact().getPhoneNumber())){
								try {
									Conversation.updateConv(TaskBar.myTextArray.get(0));
									TaskBar.smallChatWindows.get(i).receivedText(TaskBar.myTextArray.get(0));
								} catch (BadLocationException e) {
									e.printStackTrace();
									System.out.println("Updating a small chat conversation -FAILED");
								}
								TaskBar.myTextArray.remove(0);
								matchR = true;
								break;
							}
						}
						if(!matchR){
							
							Global.historyGatherText.add(TaskBar.myTextArray.get(0));
							BlueTextRequest rq = new BlueTextRequest(BlueTextRequest.REQUEST.CONTACT_CHAT_HISTORY, TaskBar.myTextArray.get(0).getSender());
							TaskBar.myTextArray.remove(0);
							TaskBar.messageHost.sendObject(rq);
													
//							TaskBar.smallChatWindows.add(new SmallChat(TaskBar.myTextArray.get(0).getReceiver(), TaskBar.myTextArray.get(0).getSender()));
//							try {
//								Conversation.updateConv(TaskBar.myTextArray.get(0));
//								TaskBar.smallChatWindows.get(TaskBar.smallChatWindows.size()-1).receivedText(TaskBar.myTextArray.get(0));
//								System.out.println("small chat window created!");
//							} catch (BadLocationException e) {
//								e.printStackTrace();
//								System.out.println("Small chat window creation -FAILED");
//							}
//							TaskBar.myTextArray.remove(0);
						}
					}
					
					// Handle incoming Contacts
					while(!TaskBar.incomingContactQueue.isEmpty()){
						Contact ct = TaskBar.incomingContactQueue.remove();
						if (ct.getLastName() == null){
							ct.setLastName("");
						}
						String first_name = ct.getFirstName();
						String perm_last = ct.getLastName();
						Boolean isUnique = true;
						//This loop is designed to remove duplicated names
						int j = 1;
						for (int i = 0; i<Global.contactAList.size();i++){
							Contact con = Global.contactAList.get(i);
							if (con.getFirstName().equals(first_name)){
								if (con.getLastName().equals(ct.getLastName())){
									//If the name already exists and the phone number is identical don't add it
									if (con.getPhoneNumber().equals(ct.getPhoneNumber())){
										isUnique = false;
										break;
									}
									//If the name already exists and there is room to add the second phone to the contact do it.
									else if (con.getSecondPhone().equals("")){
										con.setSecondPhone(ct.getPhoneNumber());
										isUnique = false;
										break;
									}
									String last_name = perm_last + " (" + Integer.toString(j) + ")";
									j++;
									ct.setLastName(last_name);
									i = 0; //set i back to 0 in case there is someone who already uses (1) we may need to use (2)
								}
							}
						}
						if (isUnique){
							addContactToListModel(ct.getFirstName(), ct.getLastName());
							Global.contactAList.add(ct);
						}
					}
					
					// Handle response objects
					processResponseQueue();
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.out.println("Synchronized method block -FAILED");
		}
	
	}		
	private void addContactToListModel(String firstName, String lastName){
		if (!firstName.equals("")){
			String newEntry = firstName + " " + lastName;
			Global.listModel.addElement(newEntry);
		}
		else if (lastName.equals("")){
			String newEntry = lastName;
			Global.listModel.addElement(newEntry);
		}
	}
	
	private void processResponseQueue()
	{
		while(!TaskBar.responseQueue.isEmpty())
		{
			BlueTextResponse resp = TaskBar.responseQueue.remove();
			REQUEST req = resp.getOriginalRequest().getRequest();
			
			if(REQUEST.BATTERY_PERCENTAGE == req)
			{
				System.out.println("Updating battery percentage to: " + resp.getBatteryLevel());
				Conversation.updateBatteryIndicator(resp.getBatteryLevel());
			}
			else{
				System.out.println("WARNING: an unknown response was received from the phone.");
			}
		}
	}
	
}
