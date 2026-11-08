package com.mineux.core;

import com.mineux.core.ui.term.DeskWindow;

/**
 * Makes one kind of an extension's desk window ({@link DeskExtensions#addPane}). An abstract
 * class, not an interface (no lambdas).
 */
public abstract class PaneFactory {

    /** A new window of this kind, when none is open on the desk. */
    public abstract DeskWindow open();
}
