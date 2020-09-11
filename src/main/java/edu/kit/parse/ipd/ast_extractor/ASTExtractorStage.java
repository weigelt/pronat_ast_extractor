package edu.kit.parse.ipd.ast_extractor;

import edu.kit.ipd.parse.luna.data.PostPipelineData;
import edu.kit.ipd.parse.luna.data.code.Method;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.ipd.parse.luna.data.ast.ASTConstants;
import edu.kit.ipd.parse.luna.data.ast.tree.ASTBlock;
import edu.kit.ipd.parse.luna.data.ast.tree.ASTBranch;
import edu.kit.ipd.parse.luna.data.ast.tree.ASTExpression;
import edu.kit.ipd.parse.luna.data.ast.tree.ASTFor;
import edu.kit.ipd.parse.luna.data.ast.tree.ASTMethodCall;
import edu.kit.ipd.parse.luna.data.ast.tree.ASTNode;
import edu.kit.ipd.parse.luna.data.ast.tree.ASTParallel;
import edu.kit.ipd.parse.luna.data.ast.tree.ASTRoot;
import edu.kit.ipd.parse.luna.data.ast.tree.ASTText;
import edu.kit.ipd.parse.luna.data.ast.tree.ASTWhile;
import edu.kit.ipd.parse.luna.data.AbstractPipelineData;
import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.data.PipelineDataCastException;
import edu.kit.ipd.parse.luna.graph.IArc;
import edu.kit.ipd.parse.luna.graph.IGraph;
import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.luna.pipeline.IPipelineStage;
import edu.kit.ipd.parse.luna.pipeline.PipelineStageException;

/**
 * @author Sebastian Weigelt
 * @author Viktor Kiesel
 */
@MetaInfServices(IPipelineStage.class)
public class ASTExtractorStage implements IPipelineStage {

	private static final String AST_COND_ELSE = ASTConstants.AST_COND_ELSE;
	private static final String AST_COND_THEN = ASTConstants.AST_COND_THEN;
	private static final String AST_COND_IF = ASTConstants.AST_COND_IF;
	private static final String AST_CHILD = ASTConstants.AST_CHILD;
	private static final String AST_TEXT = ASTConstants.AST_TEXT;
	private static final String AST_COND_BASE = ASTConstants.AST_COND_BASE;
	private static final String AST_ROOT = ASTConstants.AST_ROOT;
	private static final String AST_METHOD = ASTConstants.AST_METHOD;

	final String ID = "ASTTreeBuilderStage";
	IGraph graph;
	ASTRoot root;

	Logger logger = LoggerFactory.getLogger(ASTExtractorStage.class);

	@Override
	public void init() {

	}

	@Override
	public void exec(AbstractPipelineData data) throws PipelineStageException {
		try {
			PostPipelineData appd = data.asPostPipelineData();
			graph = appd.getGraph();

			if (graph.getNodesOfType(graph.getNodeType(AST_ROOT)).isEmpty()) {
				throw (new NullPointerException("No root AST-Node!"));
			}
			INode gRoot = graph.getNodesOfType(graph.getNodeType(AST_ROOT)).get(0);
			if (gRoot.getAttributeValue(ASTConstants.NEW_METHOD) != null) {
				appd.setMethod((Method) gRoot.getAttributeValue(ASTConstants.NEW_METHOD));
			}

			root = buildTree();
			appd.setAstRoot(root);

		} catch (PipelineDataCastException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MissingDataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private ASTRoot buildTree() {
		ASTRoot root = new ASTRoot();
		INode gRoot = graph.getNodesOfType(graph.getNodeType(AST_ROOT)).get(0);
		if (gRoot.getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN)).isEmpty()) {
			root.addChild(new ASTText("Syntaxtree could not be created, due to missing methods"));
			//		throw new NullPointerException("No Syntaxtree created!");
		} else {
			for (IArc arc : gRoot.getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN))) {
				ASTNode n = addNode(root, arc.getTargetNode());
				//			logger.debug("Added {} to root!: {}", arc.getTargetNode().getType().getName(), n);
				root.addChild(addNode(root, arc.getTargetNode()));
			}
		}
		return root;
	}

	// Separates the nodes by type
	private ASTNode addNode(ASTNode origin, INode node) {
		ASTNode ret = null;
		String type = node.getType().getName();
		switch (type) {
		case AST_ROOT:
			break;
		case AST_TEXT:
			ret = addTextNode(origin, node);
			break;
		case AST_METHOD:
			ret = addMethod(origin, node);
			break;
		case (ASTConstants.AST_WHILE_BASE):
			ret = addWhileNode(origin, node);
			break;
		case (ASTConstants.AST_FOR_BASE):
			ret = addForNode(origin, node);
			break;
		case AST_COND_BASE:
			ret = addCondNode(origin, node);
			break;
		case (ASTConstants.AST_PARALLEL_BASE):
			ret = addParallelNode(origin, node);
			break;
		}
		//		logger.debug("Added Node:" + node.getType().getName());
		ret.setPosition((double) node.getAttributeValue(ASTConstants.POSITION));
		return ret;
	}

	private ASTExpression addTextNode(ASTNode origin, INode node) {
		ASTExpression base = new ASTExpression();
		base.setParent(origin);
		String text = "";

		// use tokens for text
		//		for (IArc arc : node.getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_POINTER))) {
		//			text += arc.getTargetNode().getAttributeValue("value") + " ";
		//		}

		text = (String) node.getAttributeValue("text");

		base.addChild(new ASTText(text.trim()));
		return base;
	}

	private ASTExpression addMethod(ASTNode origin, INode node) {
		ASTExpression base = new ASTExpression();
		base.setParent(origin);
		Method method = (Method) node.getAttributeValue("method");
		ASTMethodCall mc = new ASTMethodCall();
		mc.setMethod(method);
		base.addChild(mc);
		String comment = "";
		for (IArc arc : node.getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_POINTER))) {

			comment += (String) arc.getTargetNode().getAttributeValue("value") + " ";
		}
		if (!comment.equals("")) {
			mc.setComment(comment);
		}
		return base;
	}

	private ASTWhile addWhileNode(ASTNode origin, INode node) {
		ASTWhile base = new ASTWhile();
		base.setParent(origin);
		base.setDo_while((boolean) node.getAttributeValue(ASTConstants.DO_WHILE));
		base.setNegated((boolean) node.getAttributeValue("negated"));
		for (IArc arc : node.getOutgoingArcsOfType(graph.getArcType(AST_CHILD))) {
			switch (arc.getTargetNode().getType().getName()) {
			case ASTConstants.AST_WHILE_LOOP:
				for (IArc a : arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN))) {
					//					logger.debug("Added While:" + a.getTargetNode().getType().getName());
					base.getBlock().addChild((addNode(base.getBlock(), a.getTargetNode())));
				}
				break;
			case ASTConstants.AST_WHILE_IF:
				if (!arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN)).isEmpty()) {
					for (IArc a : arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN))) {
						base.addCondition((ASTExpression) addNode(base, a.getTargetNode()));
					}

				} else {
					ASTExpression ex = new ASTExpression();
					String range = "";
					for (IArc token : arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_POINTER))) {
						range += token.getTargetNode().getAttributeValue("value") + " ";
					}
					ex.addChild(new ASTText("Error: While-If-Method not found for :" + range.trim()));
					base.addCondition(ex);
				}
				// base.setCondition((ASTExpression) addNode(base, arc.getTargetNode()));
				break;
			default:
				System.out.println("This should never happen! " + arc.getTargetNode().getType().getName());
				break;
			}
		}

		// origin.addChild(child);

		return base;

	}

	private ASTFor addForNode(ASTNode origin, INode node) {
		ASTFor base = new ASTFor();
		base.setParent(origin);
		base.setIteration((int) node.getAttributeValue(ASTConstants.ITERATIONS), "iter",
				(int) node.getAttributeValue(ASTConstants.LOOP_NUMBER));
		for (IArc arc : node.getOutgoingArcsOfType(graph.getArcType(AST_CHILD))) {

			switch (arc.getTargetNode().getType().getName()) {
			case ASTConstants.AST_FOR_LOOP:
				for (IArc a : arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN))) {
					//					logger.debug("Added For:" + a.getTargetNode().getType().getName());
					base.getBlock().addChild((addNode(base.getBlock(), a.getTargetNode())));
				}
				break;
			default:
				System.out.println("This should never happen! " + arc.getTargetNode().getType().getName());
			}
		}

		// origin.addChild(child); 
		return base;

	}

	private ASTParallel addParallelNode(ASTNode origin, INode node) {
		ASTParallel base = new ASTParallel();
		base.setParent(origin);
		for (IArc arc : node.getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD))) {
			for (IArc a : arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN))) {
				//				logger.debug("Added Parallel:" + a.getTargetNode().getType().getName());
				ASTBlock b = new ASTBlock();
				b.setParent(base); // TODO: Check if correct
				b.addChild(addNode(b, a.getTargetNode()));
				base.addSection(b);
			}
		}
		return base;
	}

	public ASTBranch addCondNode(ASTNode origin, INode node) {
		ASTBranch base = new ASTBranch();
		base.setParent(origin);

		for (IArc arc : node.getOutgoingArcsOfType(graph.getArcType(AST_CHILD))) {

			switch (arc.getTargetNode().getType().getName()) {
			case AST_COND_IF:

				if (!arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN)).isEmpty()) {
					for (IArc a : arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN))) {
						base.addCondition((ASTExpression) addNode(base, a.getTargetNode()));
					}
				} else {
					ASTExpression ex = new ASTExpression();
					String range = "";
					for (IArc token : arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_POINTER))) {
						range += token.getTargetNode().getAttributeValue("value") + " ";
					}
					ex.addChild(new ASTText("Error: Condition-If-Method not found for :" + range.trim()));
					base.addCondition(ex);
				}
				break;
			case AST_COND_THEN:
				for (IArc a : arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN))) {
					base.getThenBlock().addChild((addNode(base.getThenBlock(), a.getTargetNode())));
				}
				break;
			case AST_COND_ELSE:
				for (IArc a : arc.getTargetNode().getOutgoingArcsOfType(graph.getArcType(ASTConstants.AST_CHILD_EXTERN))) {
					base.getElseBlock().addChild((addNode(base.getElseBlock(), a.getTargetNode())));
				}
				break;
			default:
				System.out.println("This should never happen! " + arc.getTargetNode().getType().getName());
			}
		}
		return base;
	}

	@Override
	public String getID() {
		return ID;
	}

}