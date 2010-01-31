import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;

import org.jibble.pircbot.Colors;
import org.jibble.pircbot.User;
import org.jibble.socnet.Configuration;
import org.jibble.socnet.Graph;
import org.jibble.socnet.Node;
import org.jibble.socnet.SocialNetworkBot;

import AndrewCassidy.PluggableBot.DefaultPlugin;
import AndrewCassidy.PluggableBot.PluggableBot;

public class PieSpy extends DefaultPlugin{

	private Map<String, Graph> _graphs = new HashMap<String, Graph>();
	private Configuration config;

	public PieSpy() {
		try {
			Properties p = new Properties();
			String configFile = "./cfg/piespy.ini";
			p.load(new FileInputStream(configFile));
			config = new Configuration(p);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new IllegalStateException("Balls");
		} catch (NoSuchElementException e) {
			e.printStackTrace();
			throw new IllegalStateException("Balls");
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException("Balls");
		}

	}

	@Override
	public String getHelp() {
		return "";
	}

	@Override
	public void onAction(String sender, String login, String hostname,
			String target, String action) {
		if ("#&!+".indexOf(target.charAt(0)) >= 0) {
			onMessage(target, sender, login, hostname, action);
		}
	}

	@Override
	public void onAdminMessage(String sender, String login, String hostname,
			String message) {
		message = message.substring(config.password.length()).trim();
		String messageLc = message.toLowerCase();

		if (messageLc.equals("stats")) {
			// Tell the user about the Graphs currently stored.

			Iterator<String> keyIt = _graphs.keySet().iterator();
			while (keyIt.hasNext()) {
				String key = (String) keyIt.next();
				Graph graph = (Graph) _graphs.get(key);
				PluggableBot.Message(sender, key + ": " + graph.toString());
			}
		} else if (messageLc.startsWith("ignore ")
				|| messageLc.startsWith("remove ")) {
			// Add a user to the IgnoreSet and remove them from all Graphs.
			String nick = message.substring(7);
			config.ignoreSet.add(nick.toLowerCase());
			Iterator<Graph> graphIt = _graphs.values().iterator();
			while (graphIt.hasNext()) {
				Graph g = (Graph) graphIt.next();
				boolean changed = g.removeNode(new Node(nick));
				if (changed) {
					g.makeNextImage();
				}
			}
		} else if (messageLc.startsWith("draw ")) {
			// DCC SEND the latest file for a channel.
			StringTokenizer tokenizer = new StringTokenizer(message
					.substring(5));
			if (tokenizer.countTokens() >= 1) {
				String channel = tokenizer.nextToken();

				Graph graph = (Graph) _graphs.get(channel.toLowerCase());
				if (graph != null) {
					try {
						File file = (File) graph.getLastFile();
						if (file != null) {
							PluggableBot
									.Message(
											sender,
											"Trying to send \""
													+ file.getName()
													+ "\"... If you have difficultly in recieving this file via DCC, there may be a firewall between us.");
							PluggableBot.sendFileDcc(file, sender, 120000);
						} else {
							PluggableBot.Message(sender,
									"I have not generated any images for "
											+ channel + " yet.");
						}
					} catch (Exception e) {
						PluggableBot.Message(sender, "Sorry, mate: "
								+ e.toString());
					}
				} else {
					PluggableBot.Message(sender,
							"Sorry, I don't know much about that channel yet.");
				}
			} else {
				PluggableBot.Message(sender,
						"Example of correct use is \"draw <#channel>\"");
			}
		}
	}

	@Override
	public void onKick(String channel, String kickerNick, String kickerLogin,
			String kickerHostname, String recipientNick, String reason) {
		add(channel, kickerNick);
		add(channel, recipientNick);
	}

	@Override
	public void onMessage(String channel, String sender, String login,
			String hostname, String message) {
		if (config.ignoreSet.contains(sender.toLowerCase())) {
			return;
		}

		add(channel, sender);

		// Pass the message on to the InferenceHeuristics in the channel's
		// Graph.
		String key = channel.toLowerCase();
		Graph graph = (Graph) _graphs.get(key);
		graph.infer(sender, Colors.removeFormattingAndColors(message));

	}

	@Override
	public void unload() {
		// TODO Auto-generated method stub

	}

	private void add(String channel, String nick) {

		if (config.ignoreSet.contains(nick.toLowerCase())) {
			return;
		}

		Node node = new Node(nick);
		String key = channel.toLowerCase();

		// Create the Graph for this channel if it doesn't already exist.
		Graph graph = (Graph) _graphs.get(key);
		if (graph == null) {
			if (config.createRestorePoints) {
				graph = readGraph(key);
			}
			if (graph == null) {
				graph = new Graph(channel, config);
			}
			_graphs.put(key, graph);
		}

		// Add the Node to the Graph.
		graph.addNode(node);
	}

	// Read a serialized graph from disk.
	private Graph readGraph(String channel) {
		Graph g = null;
		// Try and see if the graph can be restored from file.
		try {
			String strippedChannel = channel.toLowerCase().substring(1);

			File dir = new File(config.outputDirectory, strippedChannel);
			File file = new File(dir, strippedChannel + "-restore.dat");
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
					file));
			String version = (String) ois.readObject();
			if (version.equals(SocialNetworkBot.VERSION)) {
				// Only read the object if the file is for the correct version.
				g = (Graph) ois.readObject();
			}
			ois.close();
		} catch (Exception e) {
			// Do nothing?
		}
		return g;
	}

	@Override
	public void onUserList(String channel, User[] users) {
		for (int i = 0; i < users.length; i++) {
			add(channel, users[i].getNick());
		}

	}

	@Override
	public void onNickChange(String oldNick, String login, String hostname,
			String newNick) {
		changeNick(oldNick, newNick);
	}

	private void changeNick(String oldNick, String newNick) {
		// Effect the nick change by calling the mergeNode method on all Graphs.
		Iterator<Graph> graphIt = _graphs.values().iterator();
		while (graphIt.hasNext()) {
			Graph graph = (Graph) graphIt.next();
			Node oldNode = new Node(oldNick);
			Node newNode = new Node(newNick);
			graph.mergeNode(oldNode, newNode);
		}
	}
	
	public Graph getGraph(String channel) {
		channel = channel.toLowerCase();
		return (Graph) _graphs.get(channel);
	}
	
}
