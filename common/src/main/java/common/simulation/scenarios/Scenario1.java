package common.simulation.scenarios;

import se.sics.kompics.p2p.experiment.dsl.SimulationScenario;

@SuppressWarnings("serial")
public class Scenario1 extends Scenario {

    private static SimulationScenario scenario = new SimulationScenario() {
        {

            StochasticProcess process1 = new StochasticProcess() {
                {
					eventInterArrivalTime(constant(1));
					raise(2000, Operations.peerJoin(5), uniform(13));
                }
            };
            StochasticProcess process2 = new StochasticProcess() {
                {
					eventInterArrivalTime(constant(1));
					raise(1990, Operations.peerJoin(5), uniform(13));
                }
            };


			process1.startAt(1000);
			// process2.startAfterTerminationOf(1000, process1);
			// process2.startAfterTerminationOf(2000, process1);
			// process3.startAfterTerminationOf(2000, process2);
			// process4.startAfterTerminationOf(2000, process3);
        }
    };

//-------------------------------------------------------------------
    public Scenario1() {
        super(scenario);
    }
}
