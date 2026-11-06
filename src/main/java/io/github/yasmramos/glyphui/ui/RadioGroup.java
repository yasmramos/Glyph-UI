package io.github.yasmramos.glyphui.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A mutually-exclusive group of {@link RadioButton}s.
 *
 * <p>A group guarantees that at most one of its members is selected at any
 * time: selecting a button deselects every other member. Buttons that are not
 * part of a group behave as independent toggles, so a group is only needed
 * when exclusivity matters.</p>
 *
 * <p>The group notifies a single change listener whenever the selected button
 * changes, which is the usual way an application observes the choice.</p>
 */
public class RadioGroup {

    private final List<RadioButton> buttons = new ArrayList<>();
    private Consumer<RadioButton> onChange;

    /**
     * Creates an empty group.
     */
    public RadioGroup() {
    }

    /**
     * Adds a button to the group, adopting it: if the button is already
     * selected, the other members are deselected to preserve exclusivity.
     *
     * @param button the button to add
     * @throws IllegalArgumentException when the button is null
     */
    public void add(RadioButton button) {
        if (button == null) {
            throw new IllegalArgumentException("RadioButton must not be null");
        }
        if (buttons.contains(button)) {
            return;
        }
        buttons.add(button);
        button.setGroup(this);
        if (button.isSelected()) {
            select(button);
        }
    }

    /**
     * Removes a button from the group.
     *
     * @param button the button to remove
     */
    public void remove(RadioButton button) {
        if (buttons.remove(button) && button.getGroup() == this) {
            button.setGroup(null);
        }
    }

    /**
     * @return the buttons in this group, in insertion order
     */
    public List<RadioButton> getButtons() {
        return java.util.Collections.unmodifiableList(buttons);
    }

    /**
     * @return the currently selected button, or null when none is selected
     */
    public RadioButton getSelected() {
        for (RadioButton button : buttons) {
            if (button.isSelected()) {
                return button;
            }
        }
        return null;
    }

    /**
     * Selects the given button and deselects every other member. A no-op when
     * the button already is the selection.
     *
     * @param button the button to select
     * @throws IllegalArgumentException when the button is not in this group
     */
    public void select(RadioButton button) {
        if (button == null || !buttons.contains(button)) {
            throw new IllegalArgumentException(
                    "RadioButton does not belong to this group");
        }
        if (button.isSelected() && getSelected() == button) {
            return;
        }
        for (RadioButton member : buttons) {
            member.setSelectedInternal(member == button);
        }
        Consumer<RadioButton> listener = onChange;
        if (listener != null) {
            listener.accept(button);
        }
    }

    /**
     * Clears the selection, leaving the group with no selected button.
     */
    public void clearSelection() {
        for (RadioButton member : buttons) {
            member.setSelectedInternal(false);
        }
    }

    /**
     * Registers a listener invoked after the selected button changes.
     *
     * @param onChange the listener, or null to clear it
     */
    public void setOnChange(Consumer<RadioButton> onChange) {
        this.onChange = onChange;
    }

    /**
     * @return the number of buttons in the group
     */
    public int size() {
        return buttons.size();
    }
}
