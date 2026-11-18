module com.dlsc.animatorfx.editor {
    requires com.dlsc.animatorfx.model;
    requires com.dlsc.animatorfx.player;
    requires com.dlsc.animatorfx.importer;
    requires javafx.controls;
    requires atlantafx.base;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.material2;

    exports com.dlsc.animatorfx.editor;
    exports com.dlsc.animatorfx.editor.canvas;
    exports com.dlsc.animatorfx.editor.canvas.tools;
    exports com.dlsc.animatorfx.editor.doc;
    exports com.dlsc.animatorfx.editor.inspector;
    exports com.dlsc.animatorfx.editor.inspector.fields;
    exports com.dlsc.animatorfx.editor.inspector.sections;
    exports com.dlsc.animatorfx.editor.session;
    exports com.dlsc.animatorfx.editor.timeline;
    exports com.dlsc.animatorfx.editor.ui;

    opens com.dlsc.animatorfx.editor;
}
