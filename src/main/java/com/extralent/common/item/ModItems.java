package com.extralent.common.item;


import com.extralent.common.core.handler.FuelHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.Map;

public class ModItems {

    public static GenericItem lydrix = new GenericItem("lydrix") {
        @Override
        public int getItemBurnTime(ItemStack itemStack) {
            return 2 * 60 * 20;
        }
    };
    public static GenericItem rydrixIngot = new GenericItem("rydrix_ingot");

    public static void register(IForgeRegistry<Item> registry) {
        registry.registerAll(
                lydrix,
                rydrixIngot
        );
    }

    public static void registerModels() {
        lydrix.registerItemModel();
        rydrixIngot.registerItemModel();
    }

    public static void registerFuelHandlers() {
        for (Map.Entry<Item, Integer> entry : FuelItems.FUEL_DATA.entrySet()) {
            FuelHandler.registerFuelItem(entry.getKey(), entry.getValue());
        }
    }
}