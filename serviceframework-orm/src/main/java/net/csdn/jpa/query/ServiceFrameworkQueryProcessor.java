package net.csdn.jpa.query;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a same-package {@code ModelQueries} companion and, once per package,
 * {@code ServiceFrameworkPackageAnchor}. It does not modify the model type and
 * does not load the model with {@code Class.forName}.
 *
 * <p>There is no {@code META-INF/services} registration. The ORM module compiles
 * this class as an ordinary source file, before a processor on that same
 * compilation would be runnable. Consumer builds opt in with
 * {@code -processor net.csdn.jpa.query.ServiceFrameworkQueryProcessor} and a
 * {@code -processorpath} that contains this module's classes.
 */
public final class ServiceFrameworkQueryProcessor extends AbstractProcessor {

    private final Set<String> anchorsClaimed = new HashSet<String>();
    private final Set<String> companionsClaimed = new HashSet<String>();
    private Elements elements;
    private Filer filer;
    private Messager messager;
    private RoundEnvironment round;

    public ServiceFrameworkQueryProcessor() {
    }

    @Override
    public synchronized void init(ProcessingEnvironment environment) {
        super.init(environment);
        this.elements = environment.getElementUtils();
        this.filer = environment.getFiler();
        this.messager = environment.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<String>();
        types.add("net.csdn.jpa.query.GenerateQueries");
        types.add("net.csdn.jpa.query.QueryMethod");
        return types;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        if (roundEnvironment.processingOver()) {
            return true;
        }
        this.round = roundEnvironment;
        Set<Element> models = new LinkedHashSet<Element>();
        collect(models, "net.csdn.jpa.query.GenerateQueries");
        collect(models, "net.csdn.jpa.query.QueryMethod");
        for (Element element : models) {
            generate(element);
        }
        return true;
    }

    private void collect(Set<Element> models, String annotationName) {
        TypeElement annotation = elements.getTypeElement(annotationName);
        if (annotation == null) {
            return;
        }
        models.addAll(round.getElementsAnnotatedWith(annotation));
    }

    private void generate(Element element) {
        if (!(element instanceof TypeElement) || element.getKind() != ElementKind.CLASS) {
            error(element, "ServiceFramework query declaration failed: model " + element
                    + " method - field -: @QueryMethod is only supported on a top-level class");
            return;
        }
        TypeElement model = (TypeElement) element;
        if (model.getNestingKind() != NestingKind.TOP_LEVEL) {
            error(model, QueryDeclarationException.diagnostic(model.getQualifiedName().toString(), "-", "-",
                    "companion queries support top-level model classes only"));
            return;
        }
        if (!(model.getEnclosingElement() instanceof PackageElement)) {
            error(model, QueryDeclarationException.diagnostic(model.getQualifiedName().toString(), "-", "-",
                    "model must be in a named package"));
            return;
        }
        String packageName = ((PackageElement) model.getEnclosingElement()).getQualifiedName().toString();
        String modelName = model.getQualifiedName().toString();
        if (packageName.length() == 0 || !QueryIdentifiers.isEntityName(packageName)) {
            error(model, QueryDeclarationException.diagnostic(modelName, "-", "-",
                    "model must be in a named package"));
            return;
        }
        String simpleName = model.getSimpleName().toString();
        if (QuerySources.ANCHOR_SIMPLE_NAME.equals(simpleName)) {
            error(model, QueryDeclarationException.diagnostic(modelName, "-", "-",
                    "ServiceFrameworkPackageAnchor cannot also be a query model"));
            return;
        }
        List<QueryMethodSpec> specs;
        try {
            specs = QueryMirrors.read(modelName, model, elements);
        } catch (QueryDeclarationException ex) {
            error(model, ex.getMessage());
            return;
        }
        List<QueryDeclarationException> errors = new ArrayList<QueryDeclarationException>();
        QueryMetadata metadata = QuerySchemas.compile(new LangQueryModelView(model), specs, errors);
        if (metadata == null) {
            if (errors.isEmpty()) {
                error(model, QueryDeclarationException.diagnostic(modelName, "-", "-", "query declaration failed"));
            }
            for (int i = 0; i < errors.size(); i++) {
                error(model, errors.get(i).getMessage());
            }
            return;
        }
        String companionSimple = QuerySources.companionSimpleName(simpleName);
        String companionName = packageName + "." + companionSimple;
        TypeElement existingCompanion = elements.getTypeElement(companionName);
        if (existingCompanion != null && isCompiledSource(existingCompanion)) {
            error(model, QueryDeclarationException.diagnostic(modelName, "-", "-",
                    "refusing to overwrite existing companion " + companionName));
            return;
        }
        String source;
        try {
            source = QuerySources.companionSource(packageName, simpleName, metadata);
        } catch (QueryDeclarationException ex) {
            error(model, ex.getMessage());
            return;
        }
        if (!companionsClaimed.add(companionName)) {
            return;
        }
        if (!writeSource(model, companionName, source)) {
            return;
        }
        note(model, "generated " + companionName);
        ensureAnchor(model, packageName);
    }

    private void ensureAnchor(Element model, String packageName) {
        if (!anchorsClaimed.add(packageName)) {
            return;
        }
        String anchorName = packageName + "." + QuerySources.ANCHOR_SIMPLE_NAME;
        TypeElement existing = elements.getTypeElement(anchorName);
        if (existing != null) {
            if (existing.getKind() != ElementKind.CLASS) {
                error(model, QueryDeclarationException.diagnostic(anchorName, "-", "-",
                        "ServiceFrameworkPackageAnchor conflict: existing type is " + existing.getKind()
                                + " and will not be overwritten"));
                return;
            }
            note(model, "keeping existing ServiceFrameworkPackageAnchor in " + packageName + "; not overwriting");
            return;
        }
        writeSource(model, anchorName, QuerySources.anchorSource(packageName));
    }

    private boolean writeSource(Element originating, String qualifiedName, String source) {
        try {
            JavaFileObject file = filer.createSourceFile(qualifiedName, originating);
            Writer writer = file.openWriter();
            try {
                writer.write(source);
            } finally {
                writer.close();
            }
            return true;
        } catch (IOException ex) {
            error(originating, QueryDeclarationException.diagnostic(qualifiedName, "-", "-",
                    "could not write generated source: " + ex.getClass().getSimpleName()));
            return false;
        }
    }

    private boolean isCompiledSource(TypeElement element) {
        for (Element root : round.getRootElements()) {
            if (root.equals(element)) {
                return true;
            }
        }
        return false;
    }

    private void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private void note(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.NOTE, message, element);
    }
}
