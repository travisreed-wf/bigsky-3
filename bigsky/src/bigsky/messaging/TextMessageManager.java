package bigsky.messaging;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
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

/**
 * Separate thread that runs for processing most objects that are received 
 * by the PC from the phone.  Also acts as an over-arching class for keeping
 * the primary chat window and quick chat's synchronized.
 * @author Andy Guibert, Jonathan Mielke
 */
public class TextMessageManager extends Thread
{
	public static boolean sendTexts = true;
	private static Contact blueTextRqContact;
	public void run()
	{
		boolean matchR = false;
		String phoneHLine;
		
		try {
			synchronized(this){
				while(true){
					this.wait();	
					
					// Handle response objects
					processResponseQueue();
					
					//Adding chat history from phone
                    if(!Global.phoneTextHistory.isEmpty()){
                    	int smallChatNum = 0;
                        int size = Global.phoneTextHistory.size();
                        for(int i=0; i < TaskBar.smallChatWindows.size(); i++){
                           	if(blueTextRqContact.getPhoneNumber().equals(TaskBar.smallChatWindows.get(i).getFromContact().getPhoneNumber())){
                           		smallChatNum = i;
                           		break;
                            }
                        }
                        if(smallChatNum == 0){
                        	TaskBar.smallChatWindows.add(new SmallChat(new Contact("Jonathan", "Mielke", "6185204620", ""), blueTextRqContact));
                           	TaskBar.updateAddTaskbarSmallChatWindows();
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
					
					
					// Handle incoming text messages
                    if(!TaskBar.myTextArray.isEmpty()){
                    	System.out.println("hit manager sending");
						if(TaskBar.savedInfo(Global.NOTIFICATION, Global.ON)){
							Notification notify = new Notification(TaskBar.myTextArray.get(0));
						}
							//TaskBar.trayIcon.displayMessage("New Message", TaskBar.myTextArray.get(0).getSender().getFirstName() + " " + TaskBar.myTextArray.get(0).getSender().getLastName(), MessageType.INFO);
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
//                        	Global.historyGatherText.add(TaskBar.myTextArray.get(0));
                        	BlueTextRequest rq = new BlueTextRequest(BlueTextRequest.REQUEST.CONTACT_CHAT_HISTORY, TaskBar.myTextArray.get(0).getSender());
                        	TaskBar.myTextArray.remove(0);
                        	TaskBar.messageHost.sendObject(rq);
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
	
	/**
	 * Looks at TaskBar.responseQueue which is an ArrayList of BlueTextResponses.
	 * Currently supports the following response objects:
	 * <LI>REQUEST.BATTERY_PERCENTAGE
	 * <LI>REQUEST.CONTACT_CHAT_HISTORY
	 * <LI>REQUEST.CONTACT_PICTURE
	 */
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
			else if(REQUEST.CONTACT_CHAT_HISTORY == req){
				Global.phoneTextHistory = resp.getChatHistory();
				if(!TaskBar.myTextArray.isEmpty()){
					Global.phoneTextHistory.add(TaskBar.myTextArray.get(0));
					TaskBar.myTextArray.remove(0);
				}
				 blueTextRqContact = resp.getOriginalRequest().getContact();
			}
			else if(REQUEST.CONTACT_PICTURE == req){
				
				Contact requestedContact = resp.getOriginalRequest().getContact();
				Object imageResource = resp.getImageResource();
				if(imageResource instanceof String && ((String)imageResource).equalsIgnoreCase("NO_IMG")){
					//Global.contactTOimageIcon.put(requestedContact, Global.defaultContactImage);
				}
				else if(imageResource instanceof String){
					// Deprecated code path, just use default image here
					//Global.contactTOimageIcon.put(requestedContact, Global.defaultContactImage);
				}
				else if(imageResource instanceof byte[]){
					// If a byte[] was returned by the phone, then the user
					// actually has a contact picture
					try{
						ImageIcon img = new ImageIcon((byte[]) imageResource);
						InputStream in = new ByteArrayInputStream((byte[]) imageResource);
						BufferedImage bi = ImageIO.read(in);
						img = new ImageIcon(bi.getScaledInstance(180, 180, Image.SCALE_SMOOTH));
						Global.contactTOimageIcon.put(requestedContact.getPhoneNumber(), img);
					} catch(Exception e){
						e.printStackTrace();
						//Global.contactTOimageIcon.put(requestedContact, Global.defaultContactImage);
					}
				}		
				Conversation.setThumbnailPicture(requestedContact);
			}
			else{
				System.out.println("WARNING: an unknown response was received from the phone.");
			}
		}
	}	
}
