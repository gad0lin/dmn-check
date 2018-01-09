package de.redsix.dmncheck;

import de.redsix.dmncheck.result.PrettyPrintValidationResults;
import de.redsix.dmncheck.result.ValidationResult;
import de.redsix.dmncheck.result.ValidationResultType;
import de.redsix.dmncheck.validators.*;
import de.redsix.dmncheck.validators.core.GenericValidator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "check-dmn")
class CheckerMain extends AbstractMojo {

    private final static List<GenericValidator> validators = Arrays
            .asList(DuplicateRuleValidator.instance, InputTypeDeclarationValidator.instance, OutputTypeDeclarationValidator.instance,
                    AggregationValidator.instance, AggregationOutputTypeValidator.instance, ConflictingRuleValidator.instance,
                    InputEntryTypeValidator.instance, OutputTypeDeclarationValidator.instance);

    @Parameter
    private String[] excludes;

    @Parameter
    private String[] searchPaths;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final List<Path> searchPathList = getSearchPathList().stream().map(Paths::get).collect(Collectors.toList());
        final List<String> fileNames = getFileNames(".dmn", searchPathList);
        final List<File> collect = fileNames.stream().map(File::new).collect(Collectors.toList());

        testFiles(collect);

    }

    void testFiles(final List<File> files) throws MojoExecutionException {
        boolean encounteredError = false;
        for (File file : files) {
            if (getExcludeList().contains(file.getName())) {
                getLog().info("Skipped File: " + file);
            } else {
                final DmnModelInstance dmnModelInstance = Dmn.readModelFromFile(file);
                final Map<ModelElementInstance, Map<ValidationResultType, List<ValidationResult>>> validationResults = validators.stream()
                        .flatMap(validator -> (Stream<ValidationResult>) (validator.apply(dmnModelInstance)).stream()).
                                collect(Collectors.groupingBy(ValidationResult::getElement,
                                        Collectors.groupingBy(ValidationResult::getValidationResultType)));

                    if (!validationResults.isEmpty()) {
                        getLog().error(PrettyPrintValidationResults.prettify(file, validationResults));
                        encounteredError = true;
                    }
            }
        }

        if (encounteredError) {
            throw new MojoExecutionException("Some files are not valid, see previous logs.");
        }
    }

    private List<String> getExcludeList() {
        if (excludes != null) {
            return Arrays.asList(excludes);
        } else {
            return new ArrayList<>();
        }
    }

    private List<String> getSearchPathList() {
        if (searchPaths != null) {
            return Arrays.asList(searchPaths);
        } else {
            return Collections.singletonList("");
        }
    }

    protected List<String> getFileNames(final String suffix, final List<Path> dirs) {
            return dirs.stream().flatMap(dir -> {
                try {
                    return Files.walk(dir).filter(Files::isRegularFile).map(path -> path.toAbsolutePath().toString())
                            .filter(absolutePath -> absolutePath.endsWith(suffix));
                }
                catch (IOException e) {
                    throw new RuntimeException("Could not determine DMN files.", e);
                }
            }).collect(Collectors.toList());
    }

    void setExcludes(final String[] excludes) {
        this.excludes = excludes;
    }

    void setSearchPaths(final String[] searchPaths) {
        this.searchPaths = searchPaths;
    }

}
