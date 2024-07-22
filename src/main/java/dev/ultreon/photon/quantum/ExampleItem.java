package dev.ultreon.photon.quantum;

import dev.ultreon.quantum.item.Item;
import dev.ultreon.quantum.item.UseItemContext;
import dev.ultreon.quantum.world.UseResult;

public class ExampleItem extends Item {
    public ExampleItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseResult use(UseItemContext useItemContext) {
        System.out.println("Using item");
        return super.use(useItemContext);
    }
}
