package br.com.cmabreu;
/*
 *   Copyright 2012 The Portico Project
 *
 *   This file is part of portico.
 *
 *   portico is free software; you can redistribute it and/or modify
 *   it under the terms of the Common Developer and Distribution License (CDDL) 
 *   as published by Sun Microsystems. For more information see the LICENSE file.
 *   
 *   Use of this software is strictly AT YOUR OWN RISK!!!
 *   If something bad happens you do not have permission to come crying to me.
 *   (that goes for your lawyer as well)
 *
 */


import hla.rti1516e.AttributeHandleSet;
import hla.rti1516e.CallbackModel;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.RTIambassador;
import hla.rti1516e.ResignAction;
import hla.rti1516e.RtiFactoryFactory;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Main implements IKeyReaderObserver {
	private RTIambassador rtiamb;
	private FederateAmbassador fedamb;  

	private AircraftClass aircraftClass;
	private TankClass tankClass;
	
	private static final int NUM_ITERATIONS = 10;
	private int iteration = 0;	

	private void log( String message ) 	{
		System.out.println( "> " + message );
	}
	
	public AircraftClass getAircraftClass() {
		return aircraftClass;
	}
	
	public TankClass getTankClass() {
		return tankClass;
	}
	
	// Just get the RTI Ambassador
	private void createRtiAmbassador() throws Exception {
		log( "Creating RTIambassador." );
		rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
	}

	// Connect our FederateAmbassador to the RTI
	private void connect() throws Exception {
		log( "Connecting..." );
		fedamb = new FederateAmbassador( this );
		rtiamb.connect( fedamb, CallbackModel.HLA_IMMEDIATE );
	}
	
	// Create the Federation. Will raise an error if already created
	// but you can ignore it.
	// We must pass a FOM at this time. I will use the Standard MIM.
	private void createFederation( String federationName ) throws Exception {
		log( "Creating Federation " + federationName );
		try	{
			URL[] modules = new URL[]{
				(new File("foms/HLAstandardMIM.xml")).toURI().toURL(),
				(new File("foms/unit.xml")).toURI().toURL(),
				(new File("foms/tank.xml")).toURI().toURL()
			};
			rtiamb.createFederationExecution( federationName, modules );
			log( "Created Federation." );
		} catch( FederationExecutionAlreadyExists exists ) {
			log( "Didn't create federation, it already existed." );
		} catch( MalformedURLException urle )	{
			log( "Exception loading one of the FOM modules from disk: " + urle.getMessage() );
			urle.printStackTrace();
			return;
		}
	}

	// Join our Federate to the Federation.
	// We must pass the SOM. Here we will use our SOM file.
	// I recommend you to read this file.
	private void joinFederation( String federationName, String federateName ) throws Exception  {
		URL[] joinModules = new URL[]{
			(new File("foms/aircraft.xml")).toURI().toURL()
		};
		rtiamb.joinFederationExecution( federateName, "AircraftFederateType", 
				federationName, joinModules );   
		log( "Joined Federation as " + federateName );
	}
	
	public void attributeOwnershipAcquisitionNotification( ObjectInstanceHandle theObject, 
			AttributeHandleSet securedAttributes ) {
		log("I now own the temp attibute from Tank " + theObject + ". Try to update it.");
		tankClass.updateTempValue();
	}
	
	public void attributeOwnershipDivestitureIfWanted(
			ObjectInstanceHandle theObject, AttributeHandleSet candidateAttributes, byte[] userSuppliedTag) {
		iteration++;
		log("The Tank Federate wants its attribute back. Tank " + theObject );
		log("Testing ownership of all Tanks...");
		tankClass.updateTempValue();
		log("Giving ownership...");
		try {
			rtiamb.attributeOwnershipDivestitureIfWanted(theObject, candidateAttributes);
			log("Done! It is not my problem now.");
			log("Testing ownership again...");
			tankClass.updateTempValue();
			
			try {
				Thread.sleep(800);
			} catch ( Exception e ) {
				//
			}
			
			if ( iteration < NUM_ITERATIONS ) {
				log("================== ITERATION " + iteration  + "==================");
				log("But I WANT it back!!");
				TankObject to = tankClass.getTank( theObject );
				if ( to != null ) tankClass.acquireAttribute( to );
			}
			
		} catch ( Exception e ) {
			log("Cannot accept attribute release: " + e.getMessage() );
		}
	}
	
	
	@Override
	public void notify( String key ) {
		if ( key.equals("n") ) {
			createAircraft();
		}
		
		if ( key.equals("a") ) {
			if ( tankClass.getTanks().size() == 0 ) {
				log("No Tanks discovered");
				return;
			}
			try {
				log("Starting ping-pong attribute test...");
				tankClass.publish();				
				log("================== ITERATION " + iteration  + "==================");
				log("Will try to update temp attribute for all Tanks");
				// Just to be sure we have no ownership.
				tankClass.updateTempValue();
				TankObject to = tankClass.getTanks().get(0);
				tankClass.acquireAttribute( to );
			} catch ( Exception e ) {
				e.printStackTrace();
			}
		}
		
		
	}
	
	@Override
	public void whenIdle() {
		// Update only the position
		try {
			aircraftClass.updatePosition();
			rtiamb.evokeMultipleCallbacks(0.1, 0.3);
		} catch ( Exception e ) {
			//
		}
	}
	
	private void createAircraft() {
		Random random = new Random();
		double lon = -64.265429 + ( random.nextInt(10)+1 / 1000 );
		double lat = -36.64914 + ( random.nextInt(10)+1 / 1000 );
		Position p = new Position( lon,lat);
		String id = UUID.randomUUID().toString().substring(0,5).toUpperCase();
		try {
			aircraftClass.createNew(id,id, p, AircraftObject.FOE );
		} catch ( Exception e ) {
			log("Error when creating a new Aircraft: " + e.getMessage() );
		}
	}
	
	// Run the Federate.
	public void runFederate() throws Exception	{
		String serial = UUID.randomUUID().toString().substring(0,5).toUpperCase();
		createRtiAmbassador();
		connect();
		createFederation("BasicFederation");
		joinFederation("BasicFederation", "AircraftFederate" + serial);
		
		// Start our objects managers.
		aircraftClass = new AircraftClass( rtiamb );
		tankClass = new TankClass( rtiamb );
		
		// Publish and subscribe
		publishAndSubscribe();
		
	
		System.out.println("====== AIRCRAFT FEDERATE ======");
		System.out.println("Type:");
		System.out.println("");
		System.out.println(" n + ENTER : New aircraft");
		System.out.println(" a + ENTER : Start ping-pong attribute test");
		System.out.println(" q + ENTER : Quit");
		System.out.println("");
		KeyReader kr = new KeyReader( this, "q" );
		kr.readKeyUntilQuit();
		
		// Get out!
		exitFromFederation();
	}
	
	// Exit from Federation and try to destroy it.
	// delete all objects owned by this Federate.
	private void exitFromFederation() throws Exception {
		rtiamb.resignFederationExecution( ResignAction.DELETE_OBJECTS );
		log( "Resigned from Federation" );
		try	{
			rtiamb.destroyFederationExecution( "BasicFederation" );
			log( "Destroyed Federation" );
		} catch( FederationExecutionDoesNotExist dne ) {
			log( "No need to destroy federation, it doesn't exist" );
		} catch( FederatesCurrentlyJoined fcj ){
			log( "Didn't destroy federation, federates still joined" );
		}
	}
	
	// To publish our attributes and subscribe to interactions 
	// and other Federates attributes
	private void publishAndSubscribe() throws Exception	{
		
		// Publish my own attributes
		aircraftClass.publish();
		log( "Published" );
		
		// Subscribe to the Tank attributes
		tankClass.subscribe();

		log( "Subscribed to Tanks" );
		
	}

	// This is ... ahn... the main method?
	public static void main( String[] args ) {
		System.setProperty("java.net.preferIPv4Stack" , "true");
		
		if ( args.length > 0  ) {
			Map<String, String> newenv = new HashMap<String, String>();
			newenv.put("RTI_RID_FILE", "./rti.RID" );
		}
		
		try	{
			new Main( ).runFederate();
		} catch( Exception rtie ) {
			rtie.printStackTrace();
		}
	}

	
}