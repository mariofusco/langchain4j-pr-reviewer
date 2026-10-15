package dev.langchain4j.prreviewer.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

/** The Qute templates of the web interface, checked at build time; files under {@code templates/pages}. */
@CheckedTemplate(basePath = "pages")
final class Pages {

    private Pages() {}

    static native TemplateInstance index(Views.IndexPage page);

    static native TemplateInstance run(Views.RunPage page);

    static native TemplateInstance status(WebRun run);

    static native TemplateInstance interaction(Views.InteractionView interaction);

    static native TemplateInstance report(Views.ReportPage page);

    static native TemplateInstance progressRow(WebRun.ProgressRow row);
}
