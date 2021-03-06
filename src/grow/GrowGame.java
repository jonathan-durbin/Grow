package grow;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.function.Consumer;

import exceptions.NoSuchScene;
import grow.action.Action;
import grow.action.ChangeDescription;
import grow.action.Edit;
import grow.action.Extend;
import grow.action.Print;
import grow.action.Remove;
import grow.action.Reorder;
import grow.action.Restart;
import grow.action.View;
import javafx.scene.image.Image;

/**
 * Represents: a game of Grow
 *
 * @author Jacob Glueck
 *
 */
public class GrowGame {

	/**
	 * The list of responses to unknown input
	 */
	private static final List<String> unknownInput = readList();
	/**
	 * A random number generator used to randomly pick a response from
	 * {@link #unknownInput}.
	 */
	private static final Random rnd = new Random();

	/**
	 * Reads the {@code unknown.txt} file and returns the data to be stored
	 * {@link #unknownInput}.
	 *
	 * @return the data
	 */
	private static List<String> readList() {
		Scanner s = new Scanner(GrowGame.class.getResourceAsStream("unknown.txt"));
		List<String> words = new ArrayList<>();
		while (s.hasNextLine()) {
			words.add(s.nextLine());
		}
		s.close();
		return Collections.unmodifiableList(words);
	}

	/**
	 * @return a random response from {@link #unknownInput}.
	 */
	private static String randomResponse() {
		return unknownInput.get(rnd.nextInt(unknownInput.size()));
	}

	/**
	 * The scanner used for input to this game
	 */
	private final Scanner input;
	/**
	 * The print stream used for output from this game
	 */
	private final PrintStream output;
	/**
	 * The save manager for this game
	 */
	private final SaveManager saveManager;
	/**
	 * The game.
	 */
	private Game world;
	/**
	 * The base scene, with all the built-in commands
	 */
	private final Scene base;

	/**
	 * Creates: a new game of Grow which reads input from {@code input} and
	 * prints output to {@code output}.
	 *
	 * @param input
	 *            the input.
	 * @param output
	 *            the output.
	 * @param growRoot
	 *            the root directory for the storage for the game
	 */
	public GrowGame(Scanner input, PrintStream output, File growRoot) {
		this.input = input;
		this.output = output;
		saveManager = new SaveManager(growRoot);
		world = null;
		base = new Scene("default", "For help and instructions, type \"help\".");
		String helpString = read(GrowGame.class.getResourceAsStream("help/help.txt"));
		base.rules().add(new Rule(Arrays.asList(new Print(helpString)), "help"));
		base.rules().add(new Rule(Arrays.asList(new Print(helpString + "\n" + read(GrowGame.class.getResourceAsStream("help/helpa.txt")))), "helpa"));
		base.rules().add(new Rule(Arrays.asList(new Print(read(GrowGame.class.getResourceAsStream("help/about.txt")))), "about"));
		base.rules().add(new Rule(Arrays.asList(new Print(read(GrowGame.class.getResourceAsStream("help/license.txt")))), "license"));
		base.rules().add(new Rule(Arrays.asList(saveManager.quitAction()), "quit"));
		base.rules().add(new Rule(Arrays.asList(new Restart()), "restart"));
		base.rules().add(new Rule(Arrays.asList(saveManager.readAction()), "change story"));
		base.rules().add(new Rule(Arrays.asList(saveManager.newAction()), "new"));
		base.rules().add(new Rule(Arrays.asList(new Extend()), "extend"));
		base.rules().add(new Rule(Arrays.asList(new Remove()), "remove"));
		base.rules().add(new Rule(Arrays.asList(new Edit()), "edit"));
		base.rules().add(new Rule(Arrays.asList(new Reorder()), "reorder"));
		base.rules().add(new Rule(Arrays.asList(new ChangeDescription()), "description"));
		base.rules().add(new Rule(Arrays.asList(new Print("Nothing to cancel.")), "cancel"));
		base.rules().add(new Rule(Arrays.asList(new View()), "view"));

		base.rules().add(new Rule(Arrays.asList(saveManager.importAction()), "import adventure"));
		base.rules().add(new Rule(Arrays.asList(saveManager.saveAction()), "save"));
		base.rules().add(new Rule(Arrays.asList(saveManager.importPicture()), "import image"));
		base.rules().add(new Rule(Arrays.asList(saveManager.importMusic()), "import music"));
		base.rules().add(new Rule(Arrays.asList(saveManager.clearImage()), "clear image"));
		base.rules().add(new Rule(Arrays.asList(saveManager.clearMusic()), "clear music"));
	}

	/**
	 * Effect: reads an input stream into a string. Closes the stream when done.
	 *
	 * @param in
	 *            the input stream
	 * @return the string.
	 */
	private static String read(InputStream in) {
		Scanner s = new Scanner(in);
		StringBuilder b = new StringBuilder();
		while (s.hasNextLine()) {
			b.append(s.nextLine());
			b.append("\n");
		}
		// Remove the last new line
		if (b.length() > 0 && b.charAt(b.length() - 1) == '\n') {
			b.deleteCharAt(b.length() - 1);
		}
		s.close();
		return b.toString();
	}

	/**
	 * Initializes the game. Throws an {@link IllegalStateException} if the game
	 * has already been initialized.
	 *
	 * @param injector
	 *            the injector used for prompting the user
	 *
	 * @param processor
	 *            the processor used to display the initial image, and the sound
	 * @param u
	 *            the status updater, used to signal scene or adventure changes
	 */
	public void init(Consumer<String> injector, MediaProcessor processor, StatusUpdater u) {
		if (world != null) {
			throw new IllegalStateException();
		}
		world = saveManager.init(input, output, injector);
		processor.process(world.current().image());
		processor.process(world.current().sound());

		// We have just loaded, so clear the changed status
		world.current().clearImageChanged();
		world.current().clearSoundChanged();
		u.update(world.name(), world.current().name());
	}

	/**
	 * Does not display images.
	 *
	 * @param injector
	 *            the injector to use to prompt the user
	 *
	 * @see GrowGame#init(Consumer, MediaProcessor, StatusUpdater)
	 */
	public void init(Consumer<String> injector) {
		init(injector, MediaProcessor.EMPTY, (a, s) -> {
		});
	}

	/**
	 * Effect: executes a single turn using {@code line} as the initial input,
	 * and using the input stream to get the rest of the input. If the turn
	 * results in the termination of the game, this method returns false, and
	 * resets the game so that another call to GrowGame#init(MediaProcessor,
	 * StatusUpdater) will restart it.
	 *
	 * @param line
	 *            the line to use as the initial input
	 * @param injector
	 *            the injector to use to prompt the user
	 * @param p
	 *            the processor which displays images and plays sound
	 * @param u
	 *            the status updater, used to signal scene or adventure changes
	 * @return true if the game is still going, false if the game is over
	 */
	public boolean doTurn(String line, Consumer<String> injector, MediaProcessor p, StatusUpdater u) {
		try {
			List<Action> actions = null;
			// Check to see if it is a command
			if (line.startsWith(":")) {
				actions = base.act(line.substring(1));
			}
			actions = actions == null ? world.current().act(line) : actions;
			if (actions == null) {
				output.println(randomResponse());
			} else {
				for (Action a : actions) {
					Scene prev = world.current();
					Scene next = a.act(world.current(), world, input, output, injector);
					try {
						world.move(next);
					} catch (NoSuchScene e) {
						output.println("Something bad has occurred. Please tell the developer.");
						e.printStackTrace(output);
						next = null;
					}
					if (next == null) {
						// The game is over, so reset (allow another call to
						// init)
						world = null;
						return false;
					} else {
						if (next.imageChanged() || prev != next) {
							next.clearImageChanged();
							p.process(next.image());
						}
						if (next.soundChanged() || prev != next) {
							next.clearSoundChanged();
							p.process(next.sound());
						}
						u.update(world.name(), world.current().name());
					}
				}
			}
			return true;
		} catch (Exception e) {
			output.println("Something really bad happened.");
			e.printStackTrace(output);
			output.println("Please tell the developer.");
			// Never return. Force a force quit.
			while (true) {

			}
		}
	}

	/**
	 * Does not display images
	 *
	 * @param line
	 *            the initial input
	 * @param injector
	 *            the injector to use to prompt the user
	 * @return true if the game is still running
	 * @see GrowGame#doTurn(String, Consumer, MediaProcessor, StatusUpdater)
	 */
	public boolean doTurn(String line, Consumer<String> injector) {
		return doTurn(line, injector, MediaProcessor.EMPTY, (a, s) -> {
		});
	}

	/**
	 * Starts a new game of grow that does not display images. Does not return
	 * until complete.
	 * 
	 * @param injector
	 *            the injector to use to prompt the user
	 */
	public void play(Consumer<String> injector) {
		play(injector, MediaProcessor.EMPTY, (a, s) -> {
		});
	}

	/**
	 * Starts a new game of grow that does display images using the specified
	 * image consumer. Does not return until complete.
	 * 
	 * @param injector
	 *            the injector to use to prompt the user
	 *
	 * @param p
	 *            the processor which displays images and plays sound
	 * @param u
	 *            the status updater, used to signal scene or adventure changes
	 */
	public void play(Consumer<String> injector, MediaProcessor p, StatusUpdater u) {
		// Keep doing turns until the game is over
		while (doTurn(input.nextLine(), injector, p, u)) {
			;
		}
	}

	/**
	 * Effect: tries to save and load the image into the game.
	 *
	 * @param i
	 *            the image
	 * @return true if it worked, false otherwise.
	 */
	public boolean saveImage(Image i) {
		if (world == null) {
			return false;
		}
		return saveManager.saveImage(world.current(), world, i);
	}

	/**
	 * Effect: tries to save and load the sound into the game.
	 *
	 * @param i
	 *            the sound
	 * @return true if it worked, false otherwise.
	 */
	public boolean saveSound(URI i) {
		if (world == null) {
			return false;
		}
		return saveManager.saveSound(world.current(), world, i);
	}

	/**
	 * @return the ZIP file where the current adventure is stored
	 */
	public File adventureFile() {
		return saveManager.adventureFile(world.name());
	}

	// /**
	// * Effect: imports an adventure from a zip, and switches to it.
	// *
	// * @param adventureZip
	// * the adventure zip
	// */
	// public void importAdventure(File adventureZip) {
	// saveManager.importAction(adventureZip).act(world.current(), world, input,
	// output);
	// }
}
