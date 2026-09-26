package ist.solo.routine;

/**
 * The routine seeded on first launch, so the home screen is never empty.
 * Plain on purpose: no personalities, no recommendations. Edit it by
 * importing a file with the same name.
 */
final class Templates {
    private Templates() {}

    static RoutineSpec morning() {
        RoutineSpec r = new RoutineSpec();
        r.name = "morning";
        r.weekdays = RoutineSpec.EVERY_DAY;
        r.targetMinute = 7 * 60 + 30;
        step(r, "glass of water", 2);
        step(r, "stretch", 5);
        step(r, "shower", 10);
        step(r, "get dressed", 5);
        step(r, "breakfast", 15);
        step(r, "look at today", 3);
        return r;
    }

    private static void step(RoutineSpec r, String name, int minutes) {
        r.steps.add(new RoutineSpec.Step(name, minutes * 60));
    }
}
