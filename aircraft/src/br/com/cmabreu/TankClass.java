package br.com.cmabreu;

import hla.rti1516e.AttributeHandle;
import hla.rti1516e.AttributeHandleSet;
import hla.rti1516e.AttributeHandleValueMap;
import hla.rti1516e.ObjectClassHandle;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.RTIambassador;
import hla.rti1516e.exceptions.RTIexception;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TankClass {
	// The RTI Ambassador - So we can talk to the RTI from here.
	private RTIambassador rtiamb;
	// We must hold the handle of this class 
	private ObjectClassHandle tankHandle;
	// A list of Tank objects we will instantiate
	private List<TankObject> instances;
	// An encoder helper
	private EncoderDecoder encoder;
	// We must hold the handle for all attributes of this class
	// Just Model for now.
	private AttributeHandle modelHandle;
	private AttributeHandle nameHandle;
	private AttributeHandle serialHandle;
	private AttributeHandle imageNameHandle;
	private AttributeHandle positionHandle;
	private AttributeHandle unitTypeHandle;
	private AttributeHandle tempAttributeHandle;

	
	
	// Hold all our registered attributes  
	private AttributeHandleSet attributes;

	private void log( String message ) 	{
		System.out.println( "> " + message );
	}
	
	public List<TankObject> getTanks() {
		return instances;
	}
	
	public void acquireAttribute( TankObject to ) throws Exception {
		log("Requested TempAttribute from Tank " + to.getName() + " (" + to.getHandle() + ")" );
		AttributeHandleSet ahs = rtiamb.getAttributeHandleSetFactory().create();
		ahs.add( tempAttributeHandle );
		rtiamb.attributeOwnershipAcquisition( to.getHandle(), ahs, "Attribute request".getBytes() );
	}	
	
	// Create a new Tank and register. Store it in our list of objects. 
	// Only the object owner can registerObjectInstance() of an object.
	// This method is used only by the Tank Federate.
	public ObjectInstanceHandle createNew(String name, String serial, Position position,int unitType) throws RTIexception {
		ObjectInstanceHandle coreObjectHandle = rtiamb.registerObjectInstance( tankHandle );
		TankObject to = new TankObject(coreObjectHandle, unitType);
		to.setName(name);
		to.setSerial(serial);
		to.setPosition(position);		
		log("Tank " + to.getName() + " created.");
		instances.add( to );
		return coreObjectHandle;
	}

	// Use this method if you are using this Java class ( TankClass.java ) by other
	// Federate ( Ex. a Radar ) . So when the RTI tells to your Radar Federate that 
	// there is a Tank nearby, it will give you the Tank's object handle then you can
	// store all tanks your Radar found. The two createNew() methods are exclusives:
	// This is used by Federates that not own the Tank object.
	public ObjectInstanceHandle createNew( ObjectInstanceHandle coreObjectHandle ) throws RTIexception {
		instances.add( new TankObject(coreObjectHandle, TankObject.UNKNOWN ) );

		rtiamb.requestAttributeValueUpdate(coreObjectHandle, attributes, "Request Update".getBytes() ); 

		return coreObjectHandle;
	}

	
	public TankObject getTank( ObjectInstanceHandle theObject ) {
		for ( TankObject tank : instances ) {
			if( tank.isMe( theObject ) ) {
				return tank;
			}
		}
		return null;
	}
	
	public TankObject update( AttributeHandleValueMap theAttributes, ObjectInstanceHandle theObject ) throws Exception {
		// Find the Tank instance
		for ( TankObject tank : instances ) {
			if( tank.isMe( theObject) ) {
				// Update its attributes.
				for( AttributeHandle attributeHandle : theAttributes.keySet() )	{
					// Is the attribute the Unit's Model?
					if( attributeHandle.equals( nameHandle) ) {
						tank.setName( encoder.toString( theAttributes.get(attributeHandle) ) );
					}
					if( attributeHandle.equals( serialHandle) ) {
						tank.setSerial( encoder.toString( theAttributes.get(attributeHandle) ) );
					}
					if( attributeHandle.equals( imageNameHandle) ) {
						tank.setImageName( encoder.toString( theAttributes.get(attributeHandle) ) );
					}
					if( attributeHandle.equals( tempAttributeHandle) ) {
						log("Tank "+tank.getHandle()+" sent this temp value: " + encoder.toString( theAttributes.get(attributeHandle) ) );
					}
					if( attributeHandle.equals( unitTypeHandle ) ) {
						tank.setUnitType( encoder.toInteger32( theAttributes.get(attributeHandle) ) );
					}
					if( attributeHandle.equals( positionHandle) ) {
						tank.setPosition( encoder.decodePosition( theAttributes.get(attributeHandle) ) );
					}
				}
				return tank;
			}
		}
		return null;
	}


	public void updateTempValue()  {
		for ( TankObject tank : instances  ) {
			tank.update();
			try {
				String newValue = UUID.randomUUID().toString().substring(0,5).toUpperCase();
				log("Try to update attribute from object " + tank.getHandle() );
				AttributeHandleValueMap attributes = rtiamb.getAttributeHandleValueMapFactory().create(1);
				attributes.put( tempAttributeHandle, encoder.createHLAunicodeString( newValue ).toByteArray() );			
				rtiamb.updateAttributeValues( tank.getHandle(), attributes, null );
				log("Updated.");
			} catch ( Exception e ) {
				log("It is not mine.");
			}
		}
	}
	

	// Check if a given object handle is one of mine objects
	// ( a handle of a Tank object )
	public boolean isATank( ObjectInstanceHandle objHandle ) {
		for ( TankObject tankObj : instances  ) {
			if ( tankObj.getHandle().equals( objHandle ) ) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isClassOf( ObjectClassHandle classHandle ) {
		return tankHandle.equals( classHandle );
	}
	
	public ObjectClassHandle getClassHandle() {
		return tankHandle;
	}
	
	public TankClass( RTIambassador rtiamb ) throws Exception {
		// Get the RTIAmbassador. 
		this.rtiamb = rtiamb;
		// Ask the RTI for the class handle of our Tank.
		this.tankHandle = rtiamb.getObjectClassHandle( "HLAobjectRoot.BasicUnit.Tank" );
		// Get the class handle for the Model attribute of the Tank
		this.modelHandle = rtiamb.getAttributeHandle( tankHandle, "Model" );
		this.nameHandle = rtiamb.getAttributeHandle( tankHandle, "Name" );
		this.serialHandle = rtiamb.getAttributeHandle( tankHandle, "Serial" );
		this.imageNameHandle = rtiamb.getAttributeHandle( tankHandle, "ImageName" );
		this.positionHandle = rtiamb.getAttributeHandle( tankHandle, "Position" );
		this.unitTypeHandle = rtiamb.getAttributeHandle( tankHandle, "UnitType" );
		this.tempAttributeHandle = rtiamb.getAttributeHandle( tankHandle, "TempAttribute" );
		// Create a list to store all attribute handles of the Tank
		// just to avoid to create again when publish / subscribe
		// but you may want to publish and subscribe to different attributes.
		// Why? Because this Java Class ( TankClass.java ) can be used
		// in others Federates to subscribe to Tank attributes and to store
		// discovered Tanks.
		// Ex: A Radar Federate to subscribe to Tank attributes and found
		// some Tanks around.
		this.attributes = rtiamb.getAttributeHandleSetFactory().create();
		attributes.add( modelHandle );
		attributes.add( nameHandle );
		attributes.add( serialHandle );
		attributes.add( imageNameHandle );
		attributes.add( unitTypeHandle );
		attributes.add( positionHandle );
		attributes.add( tempAttributeHandle );
		
		// Our Tank list ( created by us or discovered )
		instances = new ArrayList<TankObject>();
		// Our encoder helper.
		encoder = new EncoderDecoder();
	}
	
	
	// If you are not the Tank Federate, you can subscribe to the Tank attributes.
	public void subscribe() throws RTIexception {
		rtiamb.subscribeObjectClassAttributes( tankHandle, attributes );		
	}

	public void publish() throws RTIexception {
		log("Publishing Tank temp atribute");
		AttributeHandleSet tempAttributes = rtiamb.getAttributeHandleSetFactory().create();
		tempAttributes.add( tempAttributeHandle );
		rtiamb.publishObjectClassAttributes( tankHandle, tempAttributes );		
		log("Done.");
	}
	
}
