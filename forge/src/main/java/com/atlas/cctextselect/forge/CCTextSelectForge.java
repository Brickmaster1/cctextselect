package com.atlas.cctextselect.forge;

import com.atlas.cctextselect.CCTextSelect;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CCTextSelect.MOD_ID)
public final class CCTextSelectForge {
    public CCTextSelectForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(CCTextSelect.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        CCTextSelect.init();
    }
}
