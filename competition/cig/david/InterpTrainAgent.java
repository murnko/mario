package competition.cig.david;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.lang.Math;
import java.nio.charset.StandardCharsets;

/* This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details. */ 

import ch.idsia.ai.agents.Agent;
import ch.idsia.mario.environments.Environment;
import competition.cig.robinbaumgarten.astar.AStarSimulator;
import competition.cig.robinbaumgarten.astar.sprites.Mario;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public class InterpTrainAgent implements Agent
{
	
    protected boolean action[] = new boolean[Environment.numberOfButtons];
    protected String name = "InterpolationSVM-AStar_0";
    private AStarSimulator sim;
    private float lastX = 0;
    private float lastY = 0;
    private Integer session_counter = 0;
    private Integer samples_counter = 0;
    private svm_model currentModel, newModel;
    private svm_node[] node;
    private Double predaction;
    private Random rn = new Random();
    protected boolean SvmActions[] = new boolean[Environment.numberOfButtons];
    
    
    byte helpMario = 0;
    final byte handicap = 1;
    final Integer Interpolation = 50;
    private boolean blocked = false;
    final String Init_Model = "model0S";
    
    private List<svm_node[]> session_rec = new ArrayList<svm_node[]>();
    private List<Double> actions_rec = new ArrayList<Double>();
  
    
    public void reset()
    {
        action = new boolean[Environment.numberOfButtons];
        sim = new AStarSimulator();
        FileReader fr;
		try {
			fr = new FileReader(Init_Model);
			BufferedReader br = new BufferedReader(fr);
			currentModel = svm.svm_load_model(br);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    public boolean[] getAction(Environment observation)
    {
    	// This is the main function that is called by the mario environment.
    	// we're supposed to compute and return an action in here.
    	
    	long startTime = System.currentTimeMillis();
    	
    	// everything with "verbose" in it is debug output. 
    	// Set Levelscene.verbose to a value greater than 0 to enable some debug output.
    	String s = "Fire";
    	if (!sim.levelScene.mario.fire)
    		s = "Large";
    	if (!sim.levelScene.mario.large)
    		s = "Small";
    	if (sim.levelScene.verbose > 0) System.out.println("Next action! Simulated Mariosize: " + s);

    	boolean[] ac = new boolean[5];
    	ac[Mario.KEY_RIGHT] = true;
    	ac[Mario.KEY_SPEED] = true;
    	
    	// get the environment and enemies from the Mario API
     	byte[][] scene = observation.getLevelSceneObservationZ(0);
    	float[] enemies = observation.getEnemiesFloatPos();
		float[] realMarioPos = observation.getMarioFloatPos();
		byte[][] myScene = observation.getLevelSceneObservationZ(2);
		byte[][] myEnemies = observation.getEnemiesObservationZ(1);
		byte[][] myMerged = observation.getMergedObservationZ(2,1);
		
   	
    	if (sim.levelScene.verbose > 2) System.out.println("Simulating using action: " + sim.printAction(action));
        
    	// Advance the simulator to the state of the "real" Mario state
    	sim.advanceStep(SvmActions);   
       		
		// Handle desynchronisation of mario and the environment.
		if (sim.levelScene.mario.x != realMarioPos[0] || sim.levelScene.mario.y != realMarioPos[1])
		{
			// Stop planning when we reach the goal (just assume we're in the goal when we don't move)
			if (realMarioPos[0] == lastX && realMarioPos[1] == lastY)
				return ac;

			// Some debug output
			if (sim.levelScene.verbose > 0) System.out.println("INACURATEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE!");
			if (sim.levelScene.verbose > 0) System.out.println("Real: "+realMarioPos[0]+" "+realMarioPos[1]
			      + " Est: "+ sim.levelScene.mario.x + " " + sim.levelScene.mario.y +
			      " Diff: " + (realMarioPos[0]- sim.levelScene.mario.x) + " " + (realMarioPos[1]-sim.levelScene.mario.y));
			
			// Set the simulator mario to the real coordinates (x and y) and estimated speeds (xa and ya)
			sim.levelScene.mario.x = realMarioPos[0];
			sim.levelScene.mario.xa = (realMarioPos[0] - lastX) *0.89f;
			if (Math.abs(sim.levelScene.mario.y - realMarioPos[1]) > 0.1f)
				sim.levelScene.mario.ya = (realMarioPos[1] - lastY) * 0.85f;// + 3f;

			sim.levelScene.mario.y = realMarioPos[1];
		}
		
		// Update the internal world to the new information received
		sim.setLevelPart(scene, enemies);
		
//		if (lastX == realMarioPos[0] && lastY == realMarioPos[1]){
////			helpMario = handicap;
//			blocked = true;
//		}
//		else blocked = false;
		
		lastX = realMarioPos[0];
		lastY = realMarioPos[1];
		
		// This is the call to the simulator (where all the planning work takes place)
        action = sim.optimise();
        
        // Some time budgeting, so that we do not go over 40 ms in average.
        sim.timeBudget += 39 - (int)(System.currentTimeMillis() - startTime);
        
        
        node = GetSVMNodeM(myMerged);
        predaction = svm.svm_predict(currentModel, node);
        int keysInt = predaction.intValue();
        
        if (rn.nextInt(1000) > Interpolation + 50*session_counter && helpMario == 0) {
        	helpMario = handicap;
        }
        if (helpMario > 0) {
        	SvmActions = action;
        	helpMario --;
        }
        else {
        	SvmActions = toBinary(keysInt,5);
        }
        
        //Ignorujemu puste akcje
        //Pary stan-akcja zapisujemy do pliku
        //Zawsze od A*
        if (toInteger(SvmActions) > 0 && !blocked){
        session_rec.add(node);
        actions_rec.add(toInteger(action));
        samples_counter++;
        SaveToFile(myMerged, myEnemies, action, session_counter);
        }
        
        
        
        if (samples_counter%1000 == 0) System.out.println("Samples: " + samples_counter);
        if (samples_counter >= 10000){
        	System.out.println("Session finished" + session_counter );
        	UpdateModel();
        }
//        for (boolean key : action){
//			if (key) System.out.print(1 + " ");
//			else {System.out.print(0+ " ");}
//		}
//		System.out.println();
//		System.out.println(toInteger(action));
//        System.out.flush();
//        System.out.println(samples_counter);
       
        //Ruch wykonywany na podstawie A* || SVM
        return SvmActions;
    }

    public AGENT_TYPE getType()
    {
        return Agent.AGENT_TYPE.AI;
    }

    public String getName() 
    {        
    	return name;    
    }

    public void setName(String Name) 
    { 
    	this.name = Name;    
    }
    
    //David
    public void SaveToFile(byte[][] myScene,byte[][] myEnemies , boolean[] action, Integer SessionNb){
    	try(FileWriter fw = new FileWriter("raw_data_session_" + session_counter.toString() , true);
    		    BufferedWriter bw = new BufferedWriter(fw);
    		    PrintWriter out = new PrintWriter(bw))
    		{
    			for (byte[] row : myScene){
    				for (byte crate : row){
    					out.print(crate + " ");
    				}
    				out.println();
    			}
//    			for (byte[] row : myEnemies){
//    				for (byte crate : row){
//    					out.print(crate + " ");
//    				}
//    				out.println();
//    			}
    			
    			for (boolean key : action){
    				if (key) out.print(1 + " ");
    				else {out.print(0+ " ");}
    			}
    			out.println();
    			
    		} catch (IOException e) {
    		    //exception handling left as an exercise for the reader
    		}
    	
        }
    
	public svm_node[] GetSVMNodeM(byte[][] myScene){
	    	
			svm_node[] node = new svm_node[36];
			int index = 0;
	    	for (int i = 8; i < 14; i++ ){
	    		for (int j = 11; j <17; j++) {
	    			node[index] = new svm_node();
	    			node[index].SetValue(index, myScene[i][j]);
	    			index ++;
	    		}
	    	}
			
	    	return node;
			
    }
	private static boolean[] toBinary(int number, int base) {
        final boolean[] ret = new boolean[base];
        for (int i = 0; i < base; i++) {
            ret[base - 1 - i] = (1 << i & number) != 0;
        }
        return ret;
    }
	
	private static double toInteger(boolean[] binary) {
		int len = binary.length - 1;
		double ret = 0;
		for (int i = len; i > 0; i--) {
			if (binary[i]) ret += Math.pow(2, i-1);
        }
        return ret;
    }
	
	public void UpdateModel() {
		svm_problem prob = new svm_problem();
		svm_parameter param = new svm_parameter();
		param.cache_size = 4000;
		param.C = 1;
		param.svm_type = 0;
		param.kernel_type = 2;
		param.degree = 3;
		param.gamma =  (1/(double)36);
		param.coef0 = 0;
		param.nu = 0.5;
		param.eps = 0.001;
		param.nr_weight = 0;
		
//		param.shrinking = 0;
		prob.l = samples_counter.intValue();
		Double[] Labels = actions_rec.toArray(new Double[actions_rec.size()]);
		prob.y = Stream.of(Labels).mapToDouble(Double::doubleValue).toArray();
		svm_node[][] Features = session_rec.toArray(new svm_node[session_rec.size()][]);
		prob.x = Features;
		
		System.out.println(svm.svm_check_parameter(prob, param));
		newModel = new svm_model();
		newModel = svm.svm_train(prob,param);
		currentModel = newModel;
		samples_counter = 0;
		
		//D = D u D' i dlatego zakomentowane
		//session_rec.clear();
		//actions_rec.clear();
		session_counter++;
		try {
			svm.svm_save_model("model_" + session_counter.toString(), currentModel);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}


    }

