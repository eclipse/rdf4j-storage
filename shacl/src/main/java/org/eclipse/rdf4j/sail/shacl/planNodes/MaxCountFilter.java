package org.eclipse.rdf4j.sail.shacl.planNodes;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.shacl.plan.PlanNode;
import org.eclipse.rdf4j.sail.shacl.plan.Tuple;

public class MaxCountFilter implements PlanNode {

	PlanNode parent;
	long maxCount;


	public MaxCountFilter(PlanNode parent, long maxCount) {
		this.parent = parent;
		this.maxCount = maxCount;
	}


	@Override
	public CloseableIteration<Tuple, SailException> iterator() {
		return new CloseableIteration<Tuple, SailException>() {

			CloseableIteration<Tuple, SailException> parentIterator = parent.iterator();

			Tuple next;

			private void calculateNext(){
				if(next != null) return;

				while(parentIterator.hasNext() && next == null){
					Tuple temp = parentIterator.next();

					Literal count = (Literal) temp.line.get(1);

					if(count.longValue() > maxCount){
						next = temp;
					}

				}

			}

			@Override
			public void close() throws SailException {
				parentIterator.close();
			}

			@Override
			public boolean hasNext() throws SailException {
				calculateNext();
				return next != null;
			}

			@Override
			public Tuple next() throws SailException {
				Tuple temp = next;
				next = null;
				return temp;
			}

			@Override
			public void remove() throws SailException {

			}
		};
	}

	@Override
	public int depth() {
		return parent.depth()+1;
	}
}
