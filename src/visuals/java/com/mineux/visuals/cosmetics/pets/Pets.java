package com.mineux.visuals.cosmetics.pets;

import java.util.Arrays;
import java.util.List;

/** Every pet, by the name the settings use. Each is built once, the first time it is shown. */
public final class Pets {

    public static final List<String> NAMES = Arrays.asList(
        "off", "knight", "wizard", "astronaut", "ninja", "dog", "cat", "bunny", "penguin");

    private static final PetModel[] MADE = new PetModel[9];

    private Pets() {}

    public static PetModel get(int index) {
        if (index <= 0 || index >= MADE.length) return null;
        PetModel made = MADE[index];
        if (made == null) {
            made = make(index);
            MADE[index] = made;
        }
        return made;
    }

    private static PetModel make(int index) {
        if (index == 1) return new PetDesigns.Knight();
        if (index == 2) return new PetDesigns.Wizard();
        if (index == 3) return new PetDesigns.Astronaut();
        if (index == 4) return new PetDesigns.Ninja();
        if (index == 5) return new PetDesigns.Dog();
        if (index == 6) return new PetDesigns.Cat();
        if (index == 7) return new PetDesigns.Bunny();
        return new PetDesigns.Penguin();
    }
}
