package com.tacticnav.pmd;

import net.sourceforge.pmd.lang.java.ast.ASTBlock;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.ast.impl.javacc.JavaccToken;
import net.sourceforge.pmd.lang.ast.impl.javacc.JjtreeNode;

public class CommentsInsideMethodsRule extends AbstractJavaRule {

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        checkBlockForComments(node, node.getBody(), data);
        return super.visit(node, data);
    }

    @Override
    public Object visit(ASTConstructorDeclaration node, Object data) {
        checkBlockForComments(node, node.getBody(), data);
        return super.visit(node, data);
    }

    private void checkBlockForComments(JjtreeNode<?> declaration, ASTBlock block, Object data) {
        if (block == null) {
            return;
        }
        JavaccToken firstToken = block.getFirstToken();
        JavaccToken lastToken = block.getLastToken();

        JavaccToken token = firstToken.getNext() != null ? firstToken.getNext() : firstToken;
        while (token != null) {
            // Vérifie si ce token a un commentaire précédent
            JavaccToken comment = token.getPreviousComment();
            if (comment != null) {
                asCtx(data).addViolation(block, declaration.getImage());
                return; // Un seul warning par méthode suffit
            }
            if (token == lastToken) {
                break;
            }
            token = token.getNext();
        }
    }
}