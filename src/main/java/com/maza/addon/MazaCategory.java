package com.maza.addon;

import meteordevelopment.meteorclient.systems.modules.Category;
import net.minecraft.item.Items;

public class MazaCategory extends Category {
    public static final MazaCategory INSTANCE = new MazaCategory();

    private MazaCategory() {
        super("Maza", Items.NETHERITE_PICKAXE.getDefaultStack());
    }
}
