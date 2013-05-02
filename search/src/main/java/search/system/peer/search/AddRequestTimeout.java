package search.system.peer.search;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

public class AddRequestTimeout extends Timeout {

	private int reqId;

	public int getReqId() {
		return reqId;
	}

	public void setReqId(int reqId) {
		this.reqId = reqId;
	}

	protected AddRequestTimeout(ScheduleTimeout request, int reqId) {
		super(request);
		this.reqId = reqId;
		// TODO Auto-generated constructor stub
	}

}
