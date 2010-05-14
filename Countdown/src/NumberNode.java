/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Andrew Cassidy
 */
public class NumberNode extends SyntaxTreeNode
    {
        public double Value;

        public double Evaluate()
        {
            return Value;
        }

    @Override
        public String toString()
        {
            return Double.toString(Value);
        }

        public boolean SubTreeSearch(SyntaxTreeNode node)
        {
            return false;
        }

        public SyntaxTreeNode DeepCopy()
        {
           NumberNode copy = new NumberNode();
           copy.Value = this.Value;
           return copy;
        }
    }