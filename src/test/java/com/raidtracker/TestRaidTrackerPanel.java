package com.raidtracker;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.raidtracker.filereadwriter.FileReadWriter;
import com.raidtracker.ui.RaidTrackerPanel;
import com.raidtracker.ui.RaidUniques;
import junit.framework.TestCase;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.io.File;
import java.nio.file.Files;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestRaidTrackerPanel extends TestCase
{
    @Mock
    @Bind
    private Client client;

    @Inject
    private FileReadWriter fw;

    @Before
    public void setUp()
    {
        FileReadWriter.clearDataRootDir();
        try {
            FileReadWriter.setDataRootDir(Files.createTempDirectory("raid-data-tracker").toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
        seedCanvasbaData();
    }

    private void seedCanvasbaData() {
        fw.updateUsername("Canvasba");

        ArrayList<RaidTracker> seed = new ArrayList<>();
        seed.add(createRaidEntry("Arcane Prayer Scroll", false, "k1", "", "", "", false, ItemID.ADAMANTITE_ORE));
        seed.add(createRaidEntry("Arcane Prayer Scroll", true, "k1", "", "", "", false, ItemID.ARCANE_PRAYER_SCROLL));
        seed.add(createRaidEntry("Dexterous Prayer Scroll", false, "k2", "", "", "", false, ItemID.IRON_ORE));
        seed.add(createRaidEntry("", false, "k3", "Canvasba", "", "", false, ItemID.METAMORPHIC_DUST));
        seed.add(createRaidEntry("", false, "k3", "Random", "", "", false, ItemID.COAL));
        seed.add(createRaidEntry("", false, "k4", "", "Canvasba", "Canvasba", true, ItemID.TWISTED_ANCESTRAL_COLOUR_KIT));
        seed.add(createRaidEntry("", false, "k4", "", "Random", "Canvasba", true, ItemID.IRON_ORE));
        seed.add(createRaidEntry("", false, "k4", "", "Canvasba", "Random", false, ItemID.TWISTED_ANCESTRAL_COLOUR_KIT));
        seed.add(createRaidEntry("", false, "k4", "", "Random", "Random", false, ItemID.RUNITE_ORE));

        for (RaidTracker rt : seed) {
            fw.writeToFile(rt);
        }
    }

    private RaidTracker createRaidEntry(String specialLoot, boolean ownDrop, String killId,
                                        String dustReceiver, String kitReceiver, String petReceiver,
                                        boolean petInMyName, int itemId) {
        RaidTracker rt = new RaidTracker();
        rt.setSpecialLoot(specialLoot);
        rt.setKillCountID(killId);
        rt.setTeamSize(3);
        rt.setDate(System.currentTimeMillis());
        rt.setChallengeMode(false);
        rt.setInRaidChambers(true);
        rt.setInTheatreOfBlood(false);
        rt.setDustReceiver(dustReceiver);
        rt.setKitReceiver(kitReceiver);
        rt.setPetReceiver(petReceiver);
        rt.setPetInMyName(petInMyName);

        if (!specialLoot.isEmpty()) {
            rt.setSpecialLootReceiver("Canvasba");
            rt.setSpecialLootInOwnName(ownDrop);
        }

        ArrayList<RaidTrackerItem> lootList = new ArrayList<>();
        RaidTrackerItem item = new RaidTrackerItem();
        item.setId(itemId);
        item.setName(getItemName(itemId));
        item.setQuantity(1);
        item.setPrice(1);
        lootList.add(item);
        rt.setLootList(lootList);
        return rt;
    }

    private String getItemName(int itemId) {
        if (itemId == ItemID.ARCANE_PRAYER_SCROLL) return "Arcane Prayer Scroll";
        if (itemId == ItemID.DEXTEROUS_PRAYER_SCROLL) return "Dexterous Prayer Scroll";
        if (itemId == ItemID.METAMORPHIC_DUST) return "Metamorphic Dust";
        if (itemId == ItemID.TWISTED_ANCESTRAL_COLOUR_KIT) return "Twisted ancestral colour kit";
        if (itemId == ItemID.ADAMANTITE_ORE) return "Adamantite ore";
        if (itemId == ItemID.IRON_ORE) return "Iron ore";
        if (itemId == ItemID.RUNITE_ORE) return "Runite ore";
        return "Coal";
    }

    @Test
    public void TestFilter() throws ExecutionException, InterruptedException {
        fw.updateUsername("Canvasba");

        ArrayList<RaidTracker> l = fw.readFromFile();

        assertEquals(9, l.size());
        assertEquals("Adamantite ore", l.get(0).getLootList().get(0).getName());

        RaidTrackerPanel panel = mock(RaidTrackerPanel.class, CALLS_REAL_METHODS);
        panel.setLoaded(true);
        panel.setCoxRTList(l);
        panel.setCmFilter("CM & Normal");
        panel.setDateFilter("All Time");
        panel.setMvpFilter("Both");
        panel.setTeamSizeFilter("All sizes");
		panel.setSelectedRaidTab(RaidType.COX);

        when(panel.getUniquesList()).thenReturn(EnumSet.of(
                RaidUniques.DEX,
                RaidUniques.ARCANE,
                RaidUniques.TWISTED_BUCKLER,
                RaidUniques.DHCB,
                RaidUniques.DINNY_B,
                RaidUniques.ANCESTRAL_HAT,
                RaidUniques.ANCESTRAL_TOP,
                RaidUniques.ANCESTRAL_BOTTOM,
                RaidUniques.DRAGON_CLAWS,
                RaidUniques.ELDER_MAUL,
                RaidUniques.KODAI,
                RaidUniques.TWISTED_BOW,
                RaidUniques.DUST,
                RaidUniques.TWISTED_KIT,
                RaidUniques.OLMLET
        ));

        ItemManager IM = mock(ItemManager.class);

        panel.setItemManager(IM);

        ArrayList<RaidTracker> arcanes = panel.filterRTListByName("Arcane Prayer Scroll");
        ArrayList<RaidTracker> dexes = panel.filterRTListByName("Dexterous Prayer Scroll");
        ArrayList<RaidTracker> dusts = panel.filterDustReceivers();
        ArrayList<RaidTracker> kits = panel.filterKitReceivers();
        ArrayList<RaidTracker> pets = panel.filterPetReceivers();
        ArrayList<RaidTracker> ownArcanes = panel.filterOwnDrops(arcanes);
        ArrayList<RaidTracker> ownDexes = panel.filterOwnDrops(dexes);
        ArrayList<RaidTracker> ownDusts = panel.filterOwnDusts(dusts);
        ArrayList<RaidTracker> ownKits = panel.filterOwnKits(kits);
        ArrayList<RaidTracker> ownPets = panel.filterOwnPets(pets);


        assertEquals(2, arcanes.size());
        assertEquals(1, ownArcanes.size());
        assertEquals(1, dexes.size());
        assertEquals(0, ownDexes.size());
        assertEquals(2, dusts.size());
        assertEquals(1, ownDusts.size());
        assertEquals(4, kits.size());
        assertEquals(2, ownKits.size());
        assertEquals(4, pets.size());
        assertEquals(2, ownPets.size());

        assertEquals(4, panel.getDistinctKills(l).size());

    }
}
