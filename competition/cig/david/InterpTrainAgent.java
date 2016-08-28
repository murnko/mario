package competition.cig.david;

import java.util.List;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;

/* This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details. */ 

import ch.idsia.ai.agents.Agent;
import ch.idsia.mario.environments.Environment;
import competition.cig.robinbaumgarten.astar.AStarSimulator;
import competition.cig.robinbaumgarten.astar.sprites.Mario;
import libsvm.svm_model;
import libsvm.svm_node;

public class InterpTrainAgent implements Agent
{
    protected boolean action[] = new boolean[Environment.numberOfButtons];
    protected String name = "AStarAgent";
    private AStarSimulator sim;
    private float lastX = 0;
    private float lastY = 0;
    private String stamp = Integer.toString(LocalDateTime.now().getMinute());
    private byte session_counter = 0;
    private byte samples_counter = 0;
    private svm_model currentModel;
    
    private List<svm_node[]> session_rec = new ArrayList<svm_node[]>();
  
    
    public void reset()
    {
        action = new boolean[Environment.numberOfButtons];
        sim = new AStarSimulator();
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
    	sim.advanceStep(action);   
       		
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
        
		lastX = realMarioPos[0];
		lastY = realMarioPos[1];
		
		// This is the call to the simulator (where all the planning work takes place)
        action = sim.optimise();
        
        // Some time budgeting, so that we do not go over 40 ms in average.
        sim.timeBudget += 39 - (int)(System.currentTimeMillis() - startTime);
        
        
        session_rec.add(GetSVMTrainNodeM(myMerged));
        samples_counter++;
        if (samples_counter == 50000) 
        SaveToFile(myMerged, myEnemies, action, session_counter);
        System.out.flush();
        
        return action;
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
    public void SaveToFile(byte[][] myScene,byte[][] myEnemies , boolean[] action, byte SessionNb){
    	try(FileWriter fw = new FileWriter("raw_data_session_" + session_counter , true);
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
    
	public svm_node[] GetSVMTrainNodeM(byte[][] myScene){
	    	
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
	
	public void UpdateModel(){
		
	}


    }

