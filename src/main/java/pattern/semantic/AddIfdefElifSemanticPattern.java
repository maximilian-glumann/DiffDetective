package pattern.semantic;

import analysis.data.PatternMatch;
import diff.Lines;
import diff.difftree.DiffNode;
import evaluation.FeatureContext;
import org.prop4j.Node;

import java.util.ArrayList;
import java.util.List;

public class AddIfdefElifSemanticPattern extends SemanticPattern{

    public static final String PATTERN_NAME = "AddIfdefElifSEM";

    public AddIfdefElifSemanticPattern() {
        this.name = PATTERN_NAME;
    }

    /*
    DETECTION:
        added if node
        has an added code child
        has an added elif child
            which has an added code child
            if it also has elif/else children
                they also need to have an added code child
     */
    @Override
    public List<PatternMatch<DiffNode>> getMatches(DiffNode annotationNode) {
        List<PatternMatch<DiffNode>> patternMatches = new ArrayList<>();

        if(annotationNode.isAdd() && annotationNode.isIf()){

            boolean addedCodeInIf = false;
            DiffNode elifNode = null;
            for(DiffNode child : annotationNode.getChildren()){
                if(child.isCode() && child.isAdd()){
                    addedCodeInIf = true;
                }
                if(child.isElif() && child.isAdd()){
                    elifNode = child;
                }
            }

            List<Node> mappings = new ArrayList<>();
            mappings.add(annotationNode.getAfterFeatureMapping());
            if(elifNode == null || !addedCodeInIf || !isValidElif(elifNode, mappings)){
                return patternMatches;
            }

            final Lines diffLines = annotationNode.getLinesInDiff();
            PatternMatch<DiffNode> patternMatch = new PatternMatch<>(this,
                    diffLines.getFromInclusive(), diffLines.getToExclusive(),
                    mappings.toArray(new Node[0])
            );
            patternMatches.add(patternMatch);
        }
        return patternMatches;
    }

    private boolean isValidElif(DiffNode elifNode, List<Node> mappings) {
        boolean addedCode = false;
        DiffNode nextNode = null;

        for(DiffNode child : elifNode.getChildren()){
            if(child.isCode() && child.isAdd()){
                addedCode = true;
            }
            if((child.isElif() || child.isElse()) && child.isAdd()){
                nextNode = child;
            }
        }
        if(addedCode && nextNode != null){
            mappings.add(elifNode.getAfterFeatureMapping());
            return isValidElif(nextNode, mappings);
        }

        return addedCode;
    }

    @Override
    public FeatureContext[] getFeatureContexts(PatternMatch<DiffNode> patternMatch) {
        FeatureContext[] featureContexts = new FeatureContext[patternMatch.getFeatureMappings().length];
        for (int i = 0; i < featureContexts.length; i++) {
            featureContexts[i] = new FeatureContext(patternMatch.getFeatureMappings()[i]);
        }
        return featureContexts;
    }
}
