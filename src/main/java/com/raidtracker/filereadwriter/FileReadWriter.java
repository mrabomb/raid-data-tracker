package com.raidtracker.filereadwriter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerItem;
import com.raidtracker.RaidType;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.RuneLite.RUNELITE_DIR;
import net.runelite.client.util.Text;

@Slf4j
public class FileReadWriter {
	public static final String ALL_PROFILE_TYPES = "All Profiles";
	private static File dataRootDirOverride;

	public static void setDataRootDir(File rootDir) {
		dataRootDirOverride = rootDir;
	}

	public static void clearDataRootDir() {
		dataRootDirOverride = null;
	}

	@Getter
	private String username;
	@Getter
	private String profileName = ALL_PROFILE_TYPES;
	private String profileFolder;
	private String coxDir;
	private String tobDir;
	private String toaDir;
    private String defaultDir;

	@Inject
    @Setter
	private Gson gson;

	private File getDataRootDir() {
		if (dataRootDirOverride != null) {
			return dataRootDirOverride;
		}

		File primary = new File(RUNELITE_DIR, "raid-data-tracker");
		File legacy = new File(RUNELITE_DIR, "raid-data tracker");
		if (username != null) {
			File primaryUser = new File(primary, username);
			File legacyUser = new File(legacy, username);
			if (hasTrackedRaidData(legacyUser) && !hasTrackedRaidData(primaryUser)) {
				return legacy;
			}
		}
		if (hasTrackedRaidData(legacy) && !hasTrackedRaidData(primary)) {
			return legacy;
		}
		return primary.exists() || !legacy.exists() ? primary : legacy;
	}

	public List<String> getProfileNames() {
		return getProfileTypeNames(getDataRootDir());
	}

	private List<String> getProfileTypeNames(File rootDir) {
		List<String> profileTypes = new ArrayList<>();
		File[] profileDirs = rootDir.listFiles(File::isDirectory);
		if (profileDirs == null) {
			return profileTypes;
		}

		for (File profileDir : profileDirs) {
			for (RaidType raidType : RaidType.values()) {
				File logFile = new File(new File(profileDir, raidType.name().toLowerCase()), "raid_tracker_data.log");
				if (!logFile.isFile() && profileDir.getName().equalsIgnoreCase(raidType.name())) {
					logFile = new File(profileDir, "raid_tracker_data.log");
				}
				collectProfileTypes(logFile, profileTypes);
			}
		}
		profileTypes.sort(String::compareToIgnoreCase);
		return profileTypes;
	}

	private void collectProfileTypes(File logFile, List<String> profileTypes) {
		if (!logFile.isFile()) {
			return;
		}

			try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
				String line;
				while ((line = reader.readLine()) != null) {
					try {
						JsonObject json = new JsonParser().parse(line).getAsJsonObject();
						if (json.has("profileType") && !json.get("profileType").isJsonNull()) {
							String profileType = json.get("profileType").getAsString();
							if (!profileType.trim().isEmpty() && !profileTypes.contains(profileType)) {
								profileTypes.add(profileType);
							}
						}
					} catch (IllegalStateException | JsonSyntaxException ignored) {
					}
				}
			} catch (IOException e) {
				log.warn("Error discovering profile types from {}", logFile, e);
			}
	}

	private boolean hasTrackedRaidFolders(File profileRoot) {
		return profileRoot.isDirectory()
				&& (new File(profileRoot, "cox").isDirectory()
				|| new File(profileRoot, "tob").isDirectory()
				|| new File(profileRoot, "toa").isDirectory());
	}

	private boolean hasTrackedRaidData(File profileRoot) {
		if (!profileRoot.isDirectory()) {
			return false;
		}
		for (RaidType raidType : RaidType.values()) {
			File logFile = new File(new File(profileRoot, raidType.name().toLowerCase()), "raid_tracker_data.log");
			if (!logFile.isFile() && profileRoot.getName().equalsIgnoreCase(raidType.name())) {
				logFile = new File(profileRoot, "raid_tracker_data.log");
			}
			if (logFile.isFile() && logFile.length() > 0) {
				return true;
			}
		}
		return false;
	}

	public void setProfileName(String profileName) {
		if (profileName == null || profileName.trim().isEmpty()) {
			return;
		}
		this.profileName = profileName;
		this.profileFolder = username;
	}

	private boolean matchesSelectedProfileType(RaidTracker parsed) {
		return parsed != null && (ALL_PROFILE_TYPES.equals(profileName)
				|| (parsed.getProfileType() != null
				&& parsed.getProfileType().equalsIgnoreCase(profileName)));
	}

    public void writeToFile(RaidTracker raidTracker) {
        String fileName;
		if (raidTracker.isInTheatreOfBlood()) {
            fileName = getRaidFileName(RaidType.TOB);
		} else if (raidTracker.isInTombsOfAmascut()) {
            fileName = getRaidFileName(RaidType.TOA);
		} else if (raidTracker.isInRaidChambers()) {
            fileName = getRaidFileName(RaidType.COX);
		} else {
            fileName = getRaidFileName(null);
			log.warn("writeToFile called without an inRaid flag set.", new IllegalStateException());
		}

        try {
			log.debug("writer started");
			//use json format so serializing and deserializing is easy
			JsonParser parser = new JsonParser();
			FileWriter fw = new FileWriter(fileName,true); //the true will append the new data
			gson.toJson(parser.parse(getJSONString(raidTracker, gson, parser)), fw);
			fw.append("\n");
			fw.close();
        } catch (IOException ioe) {
			log.error("IOException: {} in writeToFile", ioe.getMessage());
		}
	}

    public String getJSONString(RaidTracker raidTracker, Gson gson, JsonParser parser) {
		JsonObject RTJson =  parser.parse(gson.toJson(raidTracker)).getAsJsonObject();

		List<RaidTrackerItem> lootList = raidTracker.getLootList();

		//------------------ temporary fix until I can get gson.tojson to work for arraylist<RaidTrackerItem> ---------
		JsonArray lootListToString = new JsonArray();

		for (RaidTrackerItem item : lootList) {
			lootListToString.add(parser.parse(gson.toJson(item, new TypeToken<RaidTrackerItem>() {
			}.getType())));
		}

		RTJson.addProperty("lootList", lootListToString.toString());

		//-------------------------------------------------------------------------------------------------------------

		//massive bodge, works for now
        return RTJson.toString().replace("\\\"", "\"").replace("\"[", "[").replace("]\"", "]");
	}

    public ArrayList<RaidTracker> readFromFile(String alternateFile, RaidType raidType) {
        String fileName = getRaidFileName(raidType);
        boolean foundReplacementUnicode = false;

		if (alternateFile.length() != 0) {
			fileName = alternateFile;
		}

		try {
			JsonParser parser = new JsonParser();
			BufferedReader bufferedreader = new BufferedReader(new FileReader(fileName));
			String line;
			ArrayList<RaidTracker> RTList = new ArrayList<>();

			while ((line = bufferedreader.readLine()) != null && line.length() > 0) {
                if (line.contains("\uFFFD")) {
                    foundReplacementUnicode = true;
                    line = line.replace("\uFFFD", " ");
                }

				try {
					RaidTracker parsed = gson.fromJson(parser.parse(line), RaidTracker.class);
					if (matchesSelectedProfileType(parsed)) {
						RTList.add(parsed);
					}
				} catch (JsonSyntaxException e) {
					log.warn("Bad line: {}", line);
				}
			}

			bufferedreader.close();

            if (foundReplacementUnicode) {
                log.info("Found replacement unicode character while reading {} log: attempting to overwrite", Text.titleCase(raidType));
                updateRTList(RTList, raidType);
            }

			return RTList;
		} catch (IOException e) {
			log.error("Error occurred reading from file", e);
			return new ArrayList<>();
		}
	}

	public ArrayList<RaidTracker> readFromFile() {
		return readFromFile("", RaidType.COX);
	}

	public ArrayList<RaidTracker> readFromFile(RaidType raidType) {
		return readFromFile("", raidType);
	}

    public void createFolders() {
		File dir = getDataRootDir();
		IGNORE_RESULT(dir.mkdir());
		if (!hasTrackedRaidFolders(dir)) {
			dir = new File(dir, username);
		}
		IGNORE_RESULT(dir.mkdir());
		IGNORE_RESULT(dir.mkdir());
		File dir_cox = new File(dir, "cox");
		File dir_tob = new File(dir, "tob");
		File dir_toa = new File(dir, "toa");
        File dir_default = new File(dir, "unknown");
		IGNORE_RESULT(dir_cox.mkdir());
		IGNORE_RESULT(dir_tob.mkdir());
		IGNORE_RESULT(dir_toa.mkdir());
        IGNORE_RESULT(dir_default.mkdir());
		this.coxDir = dir_cox.getAbsolutePath();
		this.tobDir = dir_tob.getAbsolutePath();
		this.toaDir = dir_toa.getAbsolutePath();
        this.defaultDir = dir_default.getAbsolutePath();

		try {
            IGNORE_RESULT(new File(getRaidFileName(RaidType.COX)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(RaidType.TOB)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(RaidType.TOA)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(null)).createNewFile());
		} catch (IOException e) {
			log.error("Error occurred creating new files", e);
		}
	}

	public void updateUsername(final String username) {
		this.username = username;
		profileName = ALL_PROFILE_TYPES;
		profileFolder = username;
		createFolders();
	}

	public boolean initializeExistingFlatFolders() {
		File root = getDataRootDir();
		if (!hasTrackedRaidFolders(root)) {
			return false;
		}

		this.coxDir = new File(root, "cox").getAbsolutePath();
		this.tobDir = new File(root, "tob").getAbsolutePath();
		this.toaDir = new File(root, "toa").getAbsolutePath();
		this.defaultDir = new File(root, "unknown").getAbsolutePath();
		return true;
	}

	// Used for making sure ToA loot and points is accurate
	// Initial write made on reward chest interface opened,
	// this updates after player leaves to account for purples/pets others receive
	public void updateRTLog(RaidTracker raidTracker, RaidType raidType) {
		try {
			JsonParser parser = new JsonParser();
            String fileName = getRaidFileName(raidType);
			ArrayList<RaidTracker> RTList = readFromFile(raidType);

			FileWriter fw = new FileWriter(fileName, false); // the true will append the new data

			for (RaidTracker RT : RTList) {

				if (RT.getUniqueID().equals(raidTracker.getUniqueID()) && !RT.equals(raidTracker)) {
					log.debug("writer updated log");
					RT = raidTracker;
				}

				gson.toJson(parser.parse(getJSONString(RT, gson, parser)), fw);

				fw.append("\n");
			}

			fw.close();
		} catch (IOException e) {
			log.error("Error occurred updating the log", e);
		}
	}

	public void updateRTList(ArrayList<RaidTracker> RTList, RaidType raidType) {
		try {
			JsonParser parser = new JsonParser();
            String fileName = getRaidFileName(raidType);
			FileWriter fw = new FileWriter(fileName, false); // the true will append the new data

			for (RaidTracker RT : RTList) {
				if (RT.getLootSplitPaid() > 0) {
					RT.setSpecialLootInOwnName(true);
                } else {
					// bit of a wonky check, so try to avoid with lootsplitpaid if possible
                    RT.setSpecialLootInOwnName(!RT.getLootList().isEmpty()
                            && RT.getLootList().get(0).getName().equalsIgnoreCase(RT.getSpecialLoot()));
				}

				gson.toJson(parser.parse(getJSONString(RT, gson, parser)), fw);

				fw.append("\n");
			}

			fw.close();

		} catch (IOException e) {
			log.error("Error occurred updating the log list", e);
		}
	}

    public RaidTracker getUnclaimedRewardsRT(long accountHash, RaidType raidType) {
        ArrayList<RaidTracker> logs = readFromFile(raidType);

        logs = logs.stream().filter(
            RT -> RT.getAccountHash() == accountHash
        ).collect(Collectors.toCollection(ArrayList::new));

        if (logs.isEmpty()) return null;

        RaidTracker lastLog = logs.get(logs.size() - 1);

        return lastLog.isChestOpened() ? null : lastLog;
    }

	public boolean delete(RaidType raidType) {
        File newFile = new File(getRaidFileName(raidType));

		boolean isDeleted = newFile.delete();

		try {
			IGNORE_RESULT(newFile.createNewFile());
		} catch (IOException e) {
			log.error("Error occurred creating new file", e);
		}

		return isDeleted;
	}

    public void IGNORE_RESULT(boolean b) {
    }

    public String getRaidFileName(RaidType raidType) {
        final String DATA_FILE_NAME = "/raid_tracker_data.log";

        if (raidType == null) {
            return defaultDir + DATA_FILE_NAME;
        }

		switch(raidType) {
            case COX:
                return coxDir + DATA_FILE_NAME;
			case TOB:
                return tobDir + DATA_FILE_NAME;
			case TOA:
                return toaDir + DATA_FILE_NAME;
            default:
                return defaultDir + DATA_FILE_NAME;
		}
	}
}
