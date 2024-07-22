package dev.ultreon.photon.quantum;

import dev.ultreon.quantum.ModInit;
import dev.ultreon.quantum.item.Item;
import dev.ultreon.quantum.registry.Registries;
import dev.ultreon.quantum.util.Identifier;

public class ExampleMod implements ModInit {
    @Override
    public void onInitialize() {
        Registries.ITEM.register(new Identifier("example", "example_item"), new ExampleItem(new Item.Properties().stackSize(16)));
    }
}