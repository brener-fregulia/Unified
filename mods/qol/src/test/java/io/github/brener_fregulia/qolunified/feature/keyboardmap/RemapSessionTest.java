package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brener_fregulia.qolunified.feature.keyboardmap.RemapSession.Outcome;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.RemapSession.State;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Remapping against an in-memory store; no real options are touched. */
class RemapSessionTest {
    private final Map<String, InputId> current = new LinkedHashMap<>();
    private final Map<String, InputId> defaults = new LinkedHashMap<>();
    private final List<String> writes = new ArrayList<>();
    private RemapSession session;

    private BindingCatalog catalog() {
        var bindings = new ArrayList<Binding>();
        current.forEach((id, input) -> bindings.add(new Binding(id, id, "Test", input == null ? "" : "k" + input.code(),
                input, defaults.get(id))));
        return new BindingCatalog(bindings);
    }

    @BeforeEach
    void setUp() {
        defaults.put("forward", InputId.key(26));
        defaults.put("drop", InputId.key(20));
        defaults.put("zoom", null);
        current.putAll(defaults);
        session = new RemapSession(this::catalog, new RemapSession.BindingStore() {
            @Override
            public void assign(String id, InputId input) {
                writes.add(id + "=" + input);
                current.put(id, input);
            }

            @Override
            public void reset(String id) {
                writes.add(id + " reset");
                current.put(id, defaults.get(id));
            }
        });
    }

    @Test
    void nothingIsCapturedWithoutAnExplicitChange() {
        assertEquals(Outcome.IGNORED, session.offer(InputId.key(5)));
        assertEquals(Outcome.IGNORED, session.confirm());
        assertEquals(Outcome.IGNORED, session.cancel());
        assertTrue(writes.isEmpty());
    }

    @Test
    void changeCapturesTheNextKeyboardOrMouseInput() {
        session.begin("zoom");
        assertEquals(State.CAPTURING, session.state());
        assertEquals(Outcome.APPLIED, session.offer(InputId.key(5)));
        assertEquals(InputId.key(5), current.get("zoom"));
        assertEquals(State.IDLE, session.state());

        session.begin("zoom");
        assertEquals(Outcome.APPLIED, session.offer(InputId.mouse(4)));
        assertEquals(InputId.mouse(4), current.get("zoom"));
        assertEquals(Outcome.IGNORED, session.offer(InputId.key(6)), "capture ends after one input");
    }

    @Test
    void escapeCancelsWithoutChanges() {
        session.begin("forward");
        assertEquals(Outcome.CANCELED, session.cancel());
        assertEquals(State.IDLE, session.state());
        assertTrue(writes.isEmpty());
        assertEquals(InputId.key(26), current.get("forward"));
    }

    @Test
    void aNewOverlapWaitsForConfirmationAndListsTheOthers() {
        session.begin("forward");
        assertEquals(Outcome.NEEDS_CONFIRMATION, session.offer(InputId.key(20)));
        assertEquals(State.CONFIRMING, session.state());
        assertEquals(List.of("drop"), session.pendingOverlaps().stream().map(Binding::id).toList());
        assertTrue(writes.isEmpty(), "nothing written before confirming");
        assertEquals(Outcome.IGNORED, session.offer(InputId.key(7)), "no capture while confirming");
        assertEquals(Outcome.APPLIED, session.confirm());
        assertEquals(InputId.key(20), current.get("forward"));
        assertEquals(InputState.OVERLAP, catalog().state(InputId.key(20)), "the player's choice is kept");
    }

    @Test
    void cancelingAnOverlapKeepsTheOldInput() {
        session.begin("forward");
        session.offer(InputId.key(20));
        assertEquals(Outcome.CANCELED, session.cancel());
        assertEquals(InputId.key(26), current.get("forward"));
        assertTrue(writes.isEmpty());
    }

    @Test
    void sameInputIsANoOp() {
        session.begin("forward");
        assertEquals(Outcome.APPLIED, session.offer(InputId.key(26)));
        assertTrue(writes.isEmpty());
    }

    @Test
    void clearAndResetOnlyWriteWhenTheyChangeSomething() {
        session.clear("zoom");
        session.reset("forward");
        assertTrue(writes.isEmpty());
        session.clear("forward");
        assertEquals(null, current.get("forward"));
        session.reset("forward");
        assertEquals(InputId.key(26), current.get("forward"));
        assertEquals(List.of("forward=null", "forward reset"), writes);
    }

    @Test
    void clearAndResetAreIgnoredWhileCapturing() {
        session.begin("drop");
        session.clear("forward");
        session.reset("forward");
        assertTrue(writes.isEmpty());
    }

    @Test
    void unknownMappingsCannotBeCaptured() {
        session.begin("missing");
        assertEquals(State.IDLE, session.state());
    }
}
