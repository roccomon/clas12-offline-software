package org.jlab.detector.geom.RICH;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.io.BufferedReader;
import java.io.FileReader;

import org.jlab.detector.geant4.v2.RICHGeant4Factory;
import org.jlab.detector.volume.G4Stl;
import org.jlab.detector.volume.G4Box;

import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;
import eu.mihosoft.vrl.v3d.Polygon;   
import eu.mihosoft.vrl.v3d.CSG;   
import org.jlab.detector.base.DetectorLayer;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.geometry.prim.Line3d;   

import org.jlab.geom.prim.Vector3D;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Sphere3D;   
import org.jlab.geom.prim.Triangle3D;   
import org.jlab.geom.prim.Face3D;   
import org.jlab.geom.prim.Shape3D;   

import org.jlab.utils.groups.IndexedTable;


/**
 * @author mcontalb
 */
public class RICHGeoFactory{

    private RICHGeant4Factory richfactory = new RICHGeant4Factory();
    private RICHPixelMap pixelmap = new RICHPixelMap();
    private RICHPixel pmtpixels  = null; 
    private RICHGeoParameters    geopar  = new RICHGeoParameters();
    private RICHGeoCalibration   geocal  = new RICHGeoCalibration();

    private ArrayList<ArrayList<RICHLayer>> richlayers   = new ArrayList<ArrayList<RICHLayer>>();
    private ArrayList<RICHFrame>            richframes   = new ArrayList<RICHFrame>();
    private RICHFrame survey_frame                       = new RICHFrame();

    private final static int NLAY   = RICHGeoConstants.NLAY;
    private final static int NCOMPO = RICHGeoConstants.NCOMPO;

    private Vector3D rich_survey_angle  = new Vector3D();
    private Vector3D rich_survey_shift  = new Vector3D();

    //------------------------------
    public RICHGeoFactory() {
    //------------------------------
    }


    /*
    /* generate the tracking layers (FactoryMode=0 only Aerogel and MaPMT for trajectory, FactoryMode=1  all)
    */
    //------------------------------
    public RICHGeoFactory(int FactoryMode, ConstantsManager manager, int run){
    //------------------------------

        if(FactoryMode==0){
            // add RICH tables to a different Engine
            String[] richTables = new String[]{
                    "/calibration/rich/aerogel",
                    "/calibration/rich/misalignments",
                    "/calibration/rich/parameterss"
                 };
            manager.init(Arrays.asList(richTables));
        }

        System.out.format("RICHGeoFactory: Load geometry constants from CCDB \n");
     
        int Ncalls = 0;
        geopar.load_CCDB(manager, run, Ncalls);
        geocal.load_CCDB(manager, run, Ncalls, geopar);

        if(FactoryMode>0){
            // global pixel coordinate indexes
            pixelmap.init_GlobalPixelGeo();

            // RICH survey (obsolete)
            init_Survey();
        }

        // RICH geometry organized on layers of Shape3D area and RICH components 
        init_RICHLayers(FactoryMode);

    } 


    //------------------------------
    public void init_Survey(){
    //------------------------------

        int debugMode = 0;

        if(debugMode>=1){
            System.out.format("---------------\n");
            System.out.format("Calculate RICH Alignment from Survey\n");
            System.out.format("---------------\n");
        }

        /* 
        *  Define nominal plane
        */
        Point3D RDA = new Point3D(-300.274,  168.299,  460.327);
        Point3D RDB = new Point3D(-300.309,  -168.299, 460.310);
        Point3D RDC = new Point3D(-31.102,     0., 585.886);

        Triangle3D f = new Triangle3D( RDA, RDB, RDC);
        Vector3D nomi_n = f.normal();
        Point3D  nomi_b = f.center();
        Shape3D  nomi_plane = new Shape3D(f);

        Shape3D rich_survey_plane = new Shape3D(f);
        
        /*
        *   Define surveyed plane
        */
        Point3D mRDA = new Point3D(-301.211, 168.505, 467.514);
        Point3D mRDB = new Point3D(-300.514, -167.929, 465.334);
        Point3D mRDC = new Point3D(-31.552, -0.086, 591.329);

        Triangle3D mf= new Triangle3D( mRDA, mRDB, mRDC);
        Shape3D real_plane = new Shape3D(mf);
        Vector3D real_n = mf.normal();
        Point3D  real_b = mf.center();

        if(debugMode>=1){
            // check possible deformations
            double check_a = f.point(1).distance(f.point(0));
            double check_b = f.point(2).distance(f.point(1));
            double check_c = f.point(2).distance(f.point(0));

            double checp_a = mf.point(1).distance(mf.point(0));
            double checp_b = mf.point(2).distance(mf.point(1));
            double checp_c = mf.point(2).distance(mf.point(0));

            System.out.format("Sides nominal    %8.3f %8.3f %8.3f \n",check_a, check_b, check_c);
            System.out.format("Sides real       %8.3f %8.3f %8.3f \n",checp_a, checp_b, checp_c);
        }

        // define shift among barycenters
        Vector3D diff_b = real_b.vectorFrom(nomi_b);
        //rich_survey_center = nomi_b;

        Vector3D rich_xref = new Vector3D(Math.cos(25/180.*Math.PI),0.,Math.sin(25/180.*Math.PI));
        Vector3D rich_yref = new Vector3D(0.,1.,0.);
        Vector3D rich_zref = new Vector3D(-Math.sin(25/180.*Math.PI),0.,Math.cos(25/180.*Math.PI));
        survey_frame = new RICHFrame (rich_xref, rich_yref, rich_zref, nomi_b.toVector3D());


        // define rotation angle and vector
        Vector3D dir = nomi_n.cross(real_n).asUnit();
        double ang = Math.acos(nomi_n.dot(real_n));
        Vector3D rota_n = dir.multiply(ang);

        double mrad = RICHGeoConstants.MRAD;

        rich_survey_shift = diff_b.clone();  
        rich_survey_angle = rota_n.clone();

        //Vector3d dcrich_shift = new Vector3d(global_shift[0], global_shift[1], global_shift[2]);
        //this.rich_survey_shift = new Vector3d(misa_shift.plus(dcrich_shift));
        //Vector3d dcrich_angle = new Vector3d(global_angle[0], global_angle[1], global_angle[2]);
        //this.rich_survey_angle = new Vector3d(misa_angle.plus(dcrich_angle));

        if(debugMode>=1){
            /*System.out.format(" -------------------- \n");
            System.out.format(" survey angle %s \n", rota_n.multiply(mrad).toStringBrief(2));
            System.out.format(" survey shift %.2f %7.2f \n", diff_b.x, diff_b.y, diff_b.z);
            System.out.format(" -------------------- \n");
            System.out.format(" misalg angle %7.2f %7.2f %7.2f \n", misa_angle.x*mrad, misa_angle.y*mrad, misa_angle.z*mrad);
            System.out.format(" misalg shift %7.2f %7.2f %7.2f \n", misa_shift.x, misa_shift.y, misa_shift.z);
            System.out.format(" -------------------- \n");
            System.out.format(" extern angle %7.2f %7.2f %7.2f \n", dcrich_angle.x*mrad, dcrich_angle.y*mrad, dcrich_angle.z*mrad);
            System.out.format(" extern shift %7.2f %7.2f %7.2f \n", dcrich_shift.x, dcrich_shift.y, dcrich_shift.z);
            System.out.format(" -------------------- \n");*/
            System.out.format(" survey angle %s \n", rich_survey_angle.multiply(mrad).toStringBrief(2));
            System.out.format(" survey shift %s \n", rich_survey_shift.toStringBrief(2));
            System.out.format(" -------------------- \n");
        
            System.out.format(" Check survey plane \n");
            System.out.format(" -------------------- \n");
            double thex = rich_survey_angle.dot(new Vector3D(1.,0.,0.));
            double they = rich_survey_angle.dot(new Vector3D(0.,1.,0.));
            double thez = rich_survey_angle.dot(new Vector3D(0.,0.,1.));

            System.out.format("Rot Angles NewRef %7.2f | %7.2f %7.2f %7.2f \n", ang*mrad, thex*mrad, they*mrad, thez*mrad);

            Vector3D new_n = nomi_n.clone();
            new_n.rotateZ(thez);
            new_n.rotateY(they);
            new_n.rotateX(thex);

            System.out.format("Normal nominal %s \n", nomi_n.toString());
            System.out.format("Normal real    %s \n", real_n.toString());
            System.out.format("Normal rotated %s \n", new_n.toString());
            System.out.format("\n");
            System.out.format("Baryc  nominal %s \n", nomi_b.toString());
            System.out.format("Baryc  real    %s \n", real_b.toString());
            System.out.format("Baryc  diff    %s \n", diff_b.toString());
            System.out.format("\n");

            show_Shape3D(nomi_plane, null, null);

            show_Shape3D(real_plane, null, null);

            /* test alignment angle and shift
            Face3D at = new Triangle3D( RDA, RDB, RDC);
            Shape3D test_plane = new Shape3D(at);

            //align_TrackingPlane(test_plane, -1);
            show_Shape3D(test_plane, null, null);

            double aang = 10./57.3;
            Vector3d ini = new Vector3d(Math.sin(aang), 0., Math.cos(aang));
            Vector3d anor = new Vector3d(0., 0.1, 1.);
            Vector3d nor = anor.normalized();
            Vector3d out = Transmission(ini, nor, 1.10, 1.00);
            System.out.format(" nor %s \n", toString(nor));
            System.out.format(" ini %s \n", toString(ini));
            System.out.format(" out %s \n", toString(out));
            

            double aa = Math.acos(ini.dot(nor)/ini.magnitude());
            double bb = Math.acos(out.dot(nor)/out.magnitude());
            double cc = Math.acos(ini.dot(Vector3d.Z_ONE)/ini.magnitude());
            double dd = Math.acos(out.dot(Vector3d.Z_ONE)/out.magnitude());
            System.out.format(" ini angle vn %8.3f  vz %8.3f \n", aa*57.3, bb*57.3);
            System.out.format(" out angle vn %8.3f  vz %8.3f \n", cc*57.3, dd*57.3);

            Vector3d out2 = Transmission2(ini, nor, 1.10, 1.00);
            aa = Math.acos(ini.dot(nor)/ini.magnitude());
            bb = Math.acos(out2.dot(nor)/out2.magnitude());
            cc = Math.acos(ini.dot(Vector3d.Z_ONE)/ini.magnitude());
            dd = Math.acos(out2.dot(Vector3d.Z_ONE)/out2.magnitude());
            System.out.format(" ini angle vn %8.3f  vz %8.3f \n", aa*57.3, bb*57.3);
            System.out.format(" out angle vn %8.3f  vz %8.3f \n", cc*57.3, dd*57.3);
            */


        }

    }


    //------------------------------
    public void init_RICHLayers(int FactoryMode){
    //------------------------------
    // Take RICHFactory Layers of Geant4 volumes (for GEMC) and convert in coatjava Layers 
    // of RICH components accounting for optical descriptiors plus basic tracking 
    // planes for effective ray tracing
    // ATT: to be done: aerogel cromatic dispersion, mirror reflectivity vs wavelength

        int debugMode = 0;

        /*
        * Generate the layers of components
        */
       for(int irich=1; irich<=geocal.nRICHes(); irich++){

            int isec = geocal.find_RICHSector(irich);
            if(isec==0) continue;

            ArrayList<RICHLayer> modulelayers = new ArrayList<RICHLayer>();
            richframes.add( survey_frame.clone() );

            for (int ilay=0; ilay<NLAY; ilay++){

                RICHLayer layer = new RICHLayer(isec, ilay);
                int idgea = layer.idgea();
                modulelayers.add(layer);

                if(debugMode>=1){
                    
                    System.out.format("-------------------------\n");
                    System.out.format("Create Layer %4d %4d id %4d %4d: %s %4d  dir %s \n", isec, ilay, layer.id(), idgea, 
                                       layer.name(), layer.type(), layer.get_Vinside().toStringBrief(2));
                    System.out.format("-------------------------\n");
                }

                if(FactoryMode==1 || layer.is_aerogel() || layer.is_mapmt()){

                    for (int ico=0; ico<get_RICHFactory_Size(idgea); ico++){

                        RICHComponent compo = get_RICHGeant4Component(isec, ilay, idgea, ico);
                        if(debugMode>=1)System.out.format(" Lay %3d component %3d  bary %s\n", idgea, ico, get_CSGBary(compo.get_CSGVol()));
                        compo.set_type(layer.type());

                        // define optical properties, so far only aerogel
                        if(layer.is_aerogel()){  
                            compo.set_index( geocal.get_AeroNomIndex(isec, ilay, ico) );
                            compo.set_planarity( geocal.get_AeroNomPlanarity(isec, ilay, ico)*RICHGeoConstants.CM);  
                        }else{
                            compo.set_index( 1.000);
                            compo.set_planarity( 0.000);
                        }

                        if(debugMode>=3 && layer.is_planar_mirror()){
                            if(get_PlaneMirrorSide(compo).equals(layer.name())){
                                compo.showComponent();
                                dump_StlComponent(compo.get_CSGVol());
                            }
                        }

                        // regrouping of the planar mirros into planes
                        if(layer.is_planar_mirror()){
                            if(get_PlaneMirrorSide(compo).equals(layer.name())){
                                if(debugMode>=1)System.out.format(" ---> add to Layer %3d %s id (%3d %3d) \n",ilay,layer.name(),idgea,ico);
                                layer.add(compo); 
                            }
                        }else{
                            layer.add(compo);
                        }

                    }

                    rotate_IntoSector(isec, layer);

                    /*
                     * Generate and align the basic planes for tracking 
                     */
                    // QUE: il survey non corrisponde al nominale e sovrascrive il PIVOT

                    if(debugMode>=1)System.out.format("generate surfaces for layer %d \n",ilay);

                    generate_TrackingPlane(layer);

                    if(geopar.DO_ALIGNMENT==1) align_TrackingPlane(layer);

                    store_TrackingPlane(layer);

                    if(FactoryMode>0 && layer.is_mapmt()){
                        /*
                        *  Generate Pixel map on the aligned MAPMT plane
                        */
                        List<Integer> compo_list = layer.get_CompoList();
                        Shape3D compo_shape = layer.get_TrackingSurf();
                        generate_PixelMap(layer, 0, compo_shape, compo_list);

                        if(debugMode>=1)show_Shape3D(compo_shape, null, "CC");
                    }
                }

            }
            richlayers.add(modulelayers);
            
        }
        if(debugMode>=1)show_RICH("Real RICH Geometry", "RR");

    }


    // ----------------
    public void rotate_IntoSector(int isec, RICHLayer layer){
    // ----------------

    }


    // ----------------
    public void testTraj() {
    // ----------------

        int debugMode = 0;

        for(int irich=1; irich<=geocal.nRICHes(); irich++){

            int isec = geocal.find_RICHSector(irich);
            if(isec==0) continue;

            Plane3D pl_mapmt = get_MaPMTforTraj(isec);
            pl_mapmt.show();

            Point3D pa[] = new Point3D[3];
            for (int ia=0; ia<3; ia++){
                Plane3D pl_aero = get_AeroforTraj(isec, ia);
                pl_aero.show();
                pa[ia]=pl_aero.point();
                if(debugMode>=1)System.out.format("Ref point %s \n",pa[ia].toStringBrief(2));

            }

            Point3D IP = new Point3D(0.,0.,0.);
            for (int ia=0; ia<3; ia++){
                Line3D lin = new Line3D(IP, pa[ia]);
                int iplane = select_AeroforTraj(isec, lin, lin, lin);
                if(debugMode>=1)System.out.format("For LIN %d select plane %d \n",ia,iplane);

            }
        }

    }

    
    //------------------------------
    public int find_RICHSector(int irich ){
    //------------------------------
        return geocal.find_RICHSector(irich);
    }


    //------------------------------
    public int nRICHes(){
    //------------------------------
        return geocal.nRICHes();
    }


    //------------------------------
    public IndexedTable get_richTable(){
    //------------------------------
        return geocal.richTable;
    }


    //------------------------------
    public Plane3D get_TrajPlane(int isec, int iplane) {
    //------------------------------
        
        if(geocal.find_RICHModule(isec)>0){

            if(iplane==DetectorLayer.RICH_MAPMT) return this.get_MaPMTforTraj(isec);
            else return this.get_AeroforTraj(isec, iplane);
            }
        else return null;
    }
    

    //------------------------------
    public Plane3D get_MaPMTforTraj(int isec) {
    //------------------------------

        if(geocal.find_RICHModule(isec)>0){
            RICHLayer layer = get_Layer(isec, "MAPMT");
            return layer.get_TrajPlane();
        }else{
            return null;
        }
    }


    //------------------------------
    public Plane3D get_AeroforTraj(int isec, int ilayer) {
    //------------------------------

        Plane3D layer = null;
        if(geocal.find_RICHModule(isec)>0){

            if(ilayer==DetectorLayer.RICH_AEROGEL_B1)      layer = this.get_Layer(isec, "AEROGEL_2CM_B1").get_TrajPlane();
            else if(ilayer==DetectorLayer.RICH_AEROGEL_B2) layer = this.get_Layer(isec, "AEROGEL_2CM_B2").get_TrajPlane();
            else if(ilayer==DetectorLayer.RICH_AEROGEL_L1) layer = this.get_Layer(isec, "AEROGEL_3CM_L1").get_TrajPlane();
            return layer;

        }else{
            return null;
        }
    }


    //------------------------------
    public int select_AeroforTraj(int isec, Line3D first, Line3D second, Line3D third) {
    //------------------------------

        if(geocal.find_RICHModule(isec)>0){

            RICHIntersection entra = get_Layer(isec, "AEROGEL_2CM_B2").find_Entrance(second, -2);
            if(entra!=null) return 1;

            if(entra==null) entra = get_Layer(isec, "AEROGEL_3CM_L1").find_Entrance(third, -2);
            if(entra!=null) return 2;
        }

        // return a solution plane in any case
        return 0;

    }


    //------------------------------
    public RICHPixelMap get_PixelMap() { return pixelmap; }
    //------------------------------


    //------------------------------
    public Vector3d GetPixelCenter(int ipmt, int anode){
    //------------------------------

        Vector3d Vertex = richfactory.GetPhotocatode(ipmt).getVertex(2);
        Vector3d VPixel = Vertex.plus(pmtpixels.GetPixelCenter(anode));
        //System.out.format("Std  vtx %8.3f %8.3f %8.3f \n",Vertex.x, Vertex.y, Vertex.z);
        return new Vector3d (VPixel.x, -VPixel.y, VPixel.z);

    }


    //------------------------------
    public Point3D get_Pixel_Center(int isec, int ipmt, int anode){
    //------------------------------

        int ilay = 12;
        Face3D compo_face = get_Layer(isec, ilay).get_CompoFace(ipmt-1, 0);
        Vector3d Vertex = toVector3d( compo_face.point(1) );
        
        Vector3d VPixel = Vertex.plus(pmtpixels.GetPixelCenter(anode));
        return new Point3D (VPixel.x, -VPixel.y, VPixel.z);

    }


    //------------------------------
    public Shape3D build_GlobalPlane(Shape3D plane, Vector3D orient) {
    //------------------------------
        /*
        *  build a global tracking plane from the detailed component surface
        * ATT: assumes a plane (with unique normal) with vertical (along y) edges 
        */

        int debugMode = 0;
        if(plane==null) return null;
        if(debugMode>=1)System.out.format("build_GlobalPlane: orient %s \n",orient.toStringBrief(3));

        Vector3d extre1 = new Vector3d(0.0, 0.0, 0.0);
        Vector3d extre2 = new Vector3d(0.0, 0.0, 0.0);
        Vector3d extre3 = new Vector3d(0.0, 0.0, 0.0);
        Vector3d extre4 = new Vector3d(0.0, 0.0, 0.0);

        /*
        * look for the extremes in x
        */
        double xmin = 999.0;
        double xmax = -999.0;
        for (int ifa=0; ifa<plane.size(); ifa++){
            Face3D f = plane.face(ifa);
            if(toTriangle3D(f).normal().angle(orient)>1.e-2)continue;
            for (int ipo=0; ipo<3; ipo++){

                if(f.point(ipo).x() < xmin) xmin=f.point(ipo).x();
                if(f.point(ipo).x() > xmax) xmax=f.point(ipo).x();
            }
        }  
        if(debugMode>=1)System.out.format("  x range: %7.2f %7.2f \n",xmin,xmax);

        /*
        *  look for the points at exreme y for xmin 
        */
        double ymin = 999.0;
        double ymax = -999.0;
        for (int ifa=0; ifa<plane.size(); ifa++){
            Face3D f = plane.face(ifa);
            if(toTriangle3D(f).normal().angle(orient)>1.e-2)continue;
            for (int ipo=0; ipo<3; ipo++){

                if(Math.abs(f.point(ipo).x() - xmin) < 0.5 && f.point(ipo).y() < ymin ) {
                    ymin = f.point(ipo).y();
                    extre1 = toVector3d(f.point(ipo));
                }
                if(Math.abs(f.point(ipo).x() - xmin) < 0.5 && f.point(ipo).y() > ymax ) {
                    ymax = f.point(ipo).y();
                    extre2 = toVector3d(f.point(ipo));
                }
            }
        }

        /*
        * look for the points at exreme y for xmax 
        */
        ymin = 999.0;
        ymax = -999.0;
        for (int ifa=0; ifa<plane.size(); ifa++){
            Face3D f = plane.face(ifa);
            if(toTriangle3D(f).normal().angle(orient)>1.e-2)continue;
            for (int ipo=0; ipo<3; ipo++){

                if(Math.abs(f.point(ipo).x() - xmax) < 0.5 && f.point(ipo).y() < ymin ) {
                    ymin = f.point(ipo).y();
                    extre3 = toVector3d(f.point(ipo));
                }
                if(Math.abs(f.point(ipo).x() - xmax) < 0.5 && f.point(ipo).y() > ymax ) {
                    ymax = f.point(ipo).y();
                    extre4 = toVector3d(f.point(ipo));
                }
            }
        }

        /*
        *  preserve the same normal of the original plane
        */
        Face3D half1 = new Triangle3D( toPoint3D(extre1), toPoint3D(extre2), toPoint3D(extre3));
        Face3D half2 = new Triangle3D( toPoint3D(extre2), toPoint3D(extre4), toPoint3D(extre3));
        Shape3D guess_one = new Shape3D(half1, half2);

        Face3D half3 = new Triangle3D( toPoint3D(extre3), toPoint3D(extre2), toPoint3D(extre1));
        Face3D half4 = new Triangle3D( toPoint3D(extre3), toPoint3D(extre4), toPoint3D(extre2));
        Shape3D guess_two = new Shape3D(half3, half4);

        Vector3D plane_norm = orient;
        Vector3D guess_norm = toVector3D(get_Shape3D_Normal(guess_one));
        double ang = guess_norm.angle(plane_norm)*RICHGeoConstants.RAD;

        if(debugMode>=1){
            guess_one.show();
            System.out.format("Guess one normal %s --> %7.2f \n",guess_norm.toStringBrief(2), ang*57.3);
            guess_two.show();
            Vector3D other_norm = toVector3D(get_Shape3D_Normal(guess_two));
            double other_ang = other_norm.angle(plane_norm)*RICHGeoConstants.RAD;
            System.out.format("Guess two normal %s --> %7.2f \n",other_norm.toStringBrief(2), other_ang*57.3);
        }

        if(ang<10){
            return guess_one;
        }else{
            return guess_two;
        }

    }


    //------------------------------
    public void build_GlobalPlanes(RICHLayer layer, Vector3D orient) {
    //------------------------------
        //build the tracking plane of the component with given orientation

        int debugMode = 0;


        if(debugMode>=2){
            Vector3D inside = layer.get_Vinside();
            System.out.format("build_GlobalPlane: generate global plane for layer %3d \n",layer.id());
            System.out.format("inside vect: %s \n",inside.toStringBrief(3));
            System.out.format("orient vect: %s  --> %7.3f\n",orient.toStringBrief(3), orient.angle(inside)*57.3);
        }

        Shape3D global_surf = null;

        if(layer.is_mirror()){

            if(layer.is_planar_mirror()) global_surf = copy_Shape3D(layer.merge_CompoSurfs());
            if(layer.is_spherical_mirror()) global_surf = copy_Shape3D(layer.get_NominalPlane());

        }else{
            global_surf = build_GlobalPlane(layer.merge_CompoSurfs(), orient);

            if(layer.is_aerogel()){
                Shape3D other_global = build_GlobalPlane(layer.merge_CompoSurfs(), orient.multiply(-1.0));
                merge_Shape3D(global_surf, other_global);
            }
        }

        layer.set_GlobalSurf( global_surf);
        if(debugMode>=1 && global_surf.size()>0){
            String head = String.format("GLOB %3d 0 ",layer.id());
            System.out.format("Globa %3d Normal %s \n",layer.id(),toString(get_Shape3D_Normal(global_surf)));
            for (int ifa=0; ifa<global_surf.size(); ifa++){
                System.out.format("Face %3d Normal %s \n",ifa,toTriangle3D(global_surf.face(ifa)).normal().asUnit().toStringBrief(3));
            }
            show_Shape3D(global_surf, null, head);
        }
 
    }


    //------------------------------
    public void build_CompoSpheres(RICHLayer layer) {
    //------------------------------
        //build the spherical surface of the component 

        int debugMode = 0;

        /*
        *   define the spherical surfaces when needed
        */
        if(layer.is_spherical_mirror()){

            Sphere3D sphere = new Sphere3D(-45.868, 0.0, 391.977, 270.);
            layer.set_TrackingSphere(sphere);
            for (int ico=0; ico<layer.size(); ico++){ 
                layer.set_TrackingSphere( new Sphere3D(-45.868, 0.0, 391.977, 270.), ico); 
            }

        }

        if(layer.is_aerogel()){

            for (int ico=0; ico<layer.size(); ico++){

                double radius = layer.get(ico).get_radius();
                Vector3D normal = layer.get_CompoNormal(ico);
                Vector3D center = layer.get_CompoCenter(ico, normal);

                Sphere3D sphere = new Sphere3D(center.x(), center.y(), center.z(), radius);
                if(debugMode>=1)System.out.format(" AERO lay %3d ico %3d : sphere center %s radius %7.2f \n",layer.id(),ico,toString(center), radius);
                layer.set_TrackingSphere(sphere, ico);

            }
        }

    }


    //------------------------------
    public void build_CompoSurfs(RICHLayer layer, Vector3D orient) {
    //------------------------------
        //build the tracking plane of the component with given orientation

        int debugMode = 0;

        Vector3D inside = layer.get_Vinside();

        if(debugMode>=2){
            System.out.format("build_CompoSurfs: generate tracking plane for layer %3d \n",layer.id());
            System.out.format("inside vect: %s \n",inside.toStringBrief(3));
            System.out.format("orient vect: %s  --> %7.3f\n",orient.toStringBrief(3), orient.angle(inside)*57.3);
        }

        for (int ico=0; ico<layer.size(); ico++){
            RICHComponent compo = layer.get(ico);
            Shape3D plane = new Shape3D();
            Vector3D cbary = layer.get_CompoCSGBary(ico);

            if(layer.is_spherical_mirror()){

                /*
                * Build from Nominal planes (nominal orientation)
                */
                Shape3D submir = generate_Nominal_Plane(layer, ico+1);
                for(int ifa=0; ifa<submir.size(); ifa++) plane.addFace(submir.face(ifa)); 
                     
            }else{

                /*
                * Build from CSG volumes
                */
                int ipo = 0;
                int igo = 0;
                for (Triangle3D tri: toTriangle3D(compo.get_CSGVol().getPolygons()) ){

                    Vector3D tri_norm = tri.normal().asUnit();
                    double norm_ang = tri_norm.angle(orient);
                    double norm_oppang = tri_norm.angle(orient.multiply(-1));
                    Vector3D bary_diff = (tri.center().toVector3D().sub(cbary)).asUnit();
                    double bary_dot = tri_norm.dot(bary_diff);
                    if(debugMode>=2){System.out.format("Compo %4d tri %4d  norm_ang %7.2f : %7.2f bary_dot %7.2f (%s %s)", ico, ipo, 
                                     norm_ang*57.3, norm_oppang*57.3, bary_dot*57.3, toString(tri.center()), toString(cbary));}
                    
                    /*
                    * in case of multiple surfaces (i.e. for glass skin mirrors), take the innermost.
                    */
                    if((norm_ang<1e-2 && bary_dot>0) || (layer.is_aerogel() && norm_oppang<1e-2 && bary_dot>0) ){  

                        plane.addFace(tri); 
                        if(debugMode>=2)System.out.format("    ---> take this face %3d %s\n",igo,tri_norm.toStringBrief(2));
                        igo++;
                    }else{
                        if(debugMode>=2)System.out.format("  \n");
                    }
                    ipo++;
                }
            }

            compo.set_TrackingSurf(plane);
            if(debugMode>=1 && plane.size()>0){
                System.out.format("Compo %3d %3d Normal %s \n",layer.id(),ico,toString(get_Shape3D_Normal(plane)));
                String head = String.format("COMP %3d %3d ",layer.id(),ico);
                show_Shape3D(plane, null, head);
            }
        }

    }
 
     //------------------------------
     public Vector3d get_angles(Vector3d vec) {
     //------------------------------

        Vector3d vone = vec.normalized();
        Vector3d vang = new Vector3d( Math.acos(vone.dot(Vector3d.X_ONE)), Math.acos(vone.dot(Vector3d.Y_ONE)), Math.acos(vone.dot(Vector3d.Z_ONE)));
        return vang;

     }

    //------------------------------
    public String toString(Vector3d vec, int qua) {
    //------------------------------
        if(qua==2)return String.format("%8.2f %8.2f %8.2f", vec.x, vec.y, vec.z);
        if(qua==3)return String.format("%8.3f %8.3f %8.3f", vec.x, vec.y, vec.z);
        if(qua==4)return String.format("%8.4f %8.4f %8.4f", vec.x, vec.y, vec.z);
        return String.format("%8.1f %8.1f %8.1f", vec.x, vec.y, vec.z);
 
    }

    //------------------------------
    public String toString(Vector3d vec) {
    //------------------------------
        return String.format("%8.3f %8.3f %8.3f", vec.x, vec.y, vec.z);
    }


    //------------------------------
    public String toString(Vector3D vec) {
    //------------------------------
        return String.format("%8.3f %8.3f %8.3f", vec.x(), vec.y(), vec.z());
    }

    //------------------------------
    public String toString(Point3D vec) {
    //------------------------------
        return String.format("%7.2f %7.2f %7.2f", vec.x(), vec.y(), vec.z());
    }


    //------------------------------
    public Triangle3D toTriangle3D(Face3D face){ return new Triangle3D(face.point(0), face.point(1), face.point(2)); }
    //------------------------------

    //------------------------------
    public ArrayList<Triangle3D> toTriangle3D(List<Polygon> pols){
    //------------------------------

        ArrayList<Triangle3D> trias = new ArrayList<Triangle3D>();

        for (Polygon pol: pols){
            for (int iv=2; iv<pol.vertices.size(); iv++){
                Triangle3D tri = new Triangle3D(toPoint3D(pol.vertices.get(0)), toPoint3D(pol.vertices.get(iv-1)), toPoint3D(pol.vertices.get(iv)));
                trias.add(tri);
            }
        }
        
        return trias;
    }

    //------------------------------
    public void align_Layer(RICHLayer layer){
    //------------------------------

        int debugMode = 0;

        int isec = layer.sector();
        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return;
        RICHFrame rich_frame = richframes.get(irich-1);

        /*
        *  To account for SURVEY
        */
        if(geopar.APPLY_SURVEY==1){
            if(debugMode>=1)System.out.format(" --> SURVEY %s %s \n", toString(rich_survey_shift), toString(rich_survey_angle));

            align_Element( layer.get_GlobalSurf(), survey_frame, rich_survey_angle, rich_survey_shift);
            align_Element( layer.get_TrackingSphere(), survey_frame, rich_survey_angle, rich_survey_shift);
            for(int ico=0; ico<layer.size(); ico++){
                align_Element( layer.get_TrackingSurf(ico), survey_frame, rich_survey_angle, rich_survey_shift);
                align_Element( layer.get_TrackingSphere(ico), survey_frame, rich_survey_angle, rich_survey_shift);
            }
        }


        /*
        *  To account for global RICH alignments
        */
        Vector3D rshift = geocal.get_AlignShift(isec,0,0);
        Vector3D rangle = geocal.get_AlignAngle(isec,0,0);
        if(rangle.mag()>0 || rshift.mag()>0){
            if(debugMode>=1)System.out.format(" -->  asRICH %s %s \n", rshift.toStringBrief(2), rangle.toStringBrief(3)); 

            if(debugMode>=1)System.out.format("     --> global \n");
            align_Element( layer.get_GlobalSurf(), rich_frame, rangle, rshift);
            align_Element( layer.get_TrackingSphere(), rich_frame, rangle, rshift);

            for(int ico=0; ico<layer.size(); ico++){
                if(debugMode>=1)System.out.format("     --> compo %3d \n",ico);
                align_Element( layer.get_TrackingSurf(ico), rich_frame, rangle, rshift);
                align_Element( layer.get_TrackingSphere(ico), rich_frame, rangle, rshift);
            }
        }


        /*
        *  To account for Layer alignment 
        */
        int ilay = layer.id();
        RICHFrame lframe = layer.generate_LocalRef();
        Vector3D lshift = geocal.get_AlignShift(isec,ilay+1,0);
        Vector3D langle = geocal.get_AlignAngle(isec,ilay+1,0);
        if(langle.mag()>0 || lshift.mag()>0){
            if(debugMode>=1){System.out.format("    -->  asLayer  %d  %s %s \n", ilay, lshift.toStringBrief(2), langle.toStringBrief(3)); }

            if(debugMode>=1)System.out.format("     --> global \n");
            align_Element( layer.get_GlobalSurf(), lframe, langle, lshift);
            align_Element( layer.get_TrackingSphere(), lframe, langle, lshift);

            for(int ico=0; ico<layer.size(); ico++){
                if(debugMode>=1)System.out.format("     --> compo %3d \n",ico);
                align_Element( layer.get_TrackingSurf(ico), lframe, langle, lshift);
                align_Element( layer.get_TrackingSphere(ico), lframe, langle, lshift);
            }
        }


        /*
        *  To account for single Component alignment 
        */
        if(layer.is_spherical_mirror()){
            for(int ico=0; ico<layer.size(); ico++){

                RICHFrame cframe = layer.generate_LocalRef(ico);
                Vector3D cshift = geocal.get_AlignShift(isec,ilay+1,ico+1);
                Vector3D cangle = geocal.get_AlignAngle(isec,ilay+1,ico+1);

                if(cangle.mag()==0 && cshift.mag()==0)continue;
                if(debugMode==1){System.out.format("       -->  asCompo %3d  %s %s \n", ico, cshift.toStringBrief(2), cangle.toStringBrief(3));}

                align_Element( layer.get_TrackingSurf(ico), cframe, cangle, cshift);
                align_Element( layer.get_TrackingSphere(ico), cframe, cangle, cshift);
                if(!layer.CheckSphere(ico))System.out.format("Misalignment issue for lay %3d compo %3d \n",ilay,ico);
            }
        }
    }


    //------------------------------
    public void generate_TrackingPlane(RICHLayer layer){
    //------------------------------

        int debugMode = 0;

        int isec = layer.sector();
        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return;
        RICHFrame rich_frame = richframes.get(irich-1);

        Vector3D orient = layer.get_Vinside();
        if(debugMode>=1){
            System.out.format("------------------------\n");
            System.out.format("Generate tracking for sector %3d  layer %4d  %s view %s \n", layer.sector(), layer.id(), 
                                        layer.name(), orient.toStringBrief(3));
            System.out.format("------------------------\n");
        }

        /*
        *  Nominal plane just for reference 
        */
        layer.set_NominalPlane( generate_Nominal_Plane(layer, 0) );


        /*
        *  For each component, group faces with normal and position vs barycenter along orient
        */
        build_CompoSurfs(layer, orient);
        

        /*
        *  Generate a global plane for fast tracking without gaps
        *  In case of aerogel add the second global face 
        */
        build_GlobalPlanes(layer, orient);


        /*
        *  Select the pivot for the RICH rotations
        */
        if(layer.is_mapmt()) {
            if(geopar.ALIGN_PMT_PIVOT==1) rich_frame.set_bref(layer.get_SurfBary());
            if(debugMode>=1)System.out.format("RICH PIVOT %s \n",rich_frame.bref().toStringBrief(2));
        }


        /*
        *   define the spherical surfaces when needed
        */
        build_CompoSpheres(layer);

    }

    //------------------------------
    public void align_TrackingPlane(RICHLayer layer) {
    //------------------------------

        int debugMode = 0;

        /*
        *  Apply alignment around given PIVOT
        */

        if(debugMode>=1){
            System.out.format("------------------------\n");
            System.out.format("Align tracking for Layer %d %s\n", layer.id(), layer.name());
            System.out.format("------------------------\n");
        }

        /*
        *  Misalign surfs as required
        */
        align_Layer(layer);

        /*
        *  Check alignment effect on survey plane
        *//*
        if(debugMode>=1){
            System.out.format("Centre %s\n",rich_misa_center.toStringBrief(2));
            double mrad = RICHConstants.MRAD;
            System.out.format(" rich   angle %7.2f %7.2f %7.2f \n", this.rich_misa_angle.x*mrad, this.rich_misa_angle.y*mrad, this.rich_misa_angle.z*mrad);
            System.out.format(" rich   shift %7.2f %7.2f %7.2f \n", this.rich_misa_shift.x, this.rich_misa_shift.y, this.rich_misa_shift.z);
            show_Shape3D(rich_survey_plane,"Nominal survey", null);
            //align_TrackingPlane(rich_survey_plane, 0);
            show_Shape3D(rich_survey_plane,"Misalig survey", null);
        }*/

    }


    //------------------------------
    public void store_TrackingPlane(RICHLayer layer){
    //------------------------------

        int debugMode = 0;

        /*
        *  Store the composite tracking planes
        */

        if(debugMode>=1){
            System.out.format("------------------------\n");
            System.out.format("Store    tracking for Layer %d %s\n", layer.id(), layer.name());
            System.out.format("------------------------\n");
        }

        /* 
        *  Store alignmed tracking surfaces for fast tracking 
        */
        layer.set_TrackingSurf( layer.merge_CompoSurfs());
        layer.set_CompoList( layer.merge_CompoList());
           
    }


    //------------------------------
    public void generate_PixelMap(RICHLayer layer, int ico, Shape3D compo_plane, List<Integer> compo_list) {
    //------------------------------
    // generate the MAPMT pixel map starting from the corresponding
    // facet of the MAPMT plane aligned in the space
    // QUE: assumes that the MAPMT facets have always the same ordering ?

        int debugMode = 0;

        if(layer.is_mapmt()){

            if(debugMode>=1){
                System.out.format("------------------------\n");
                System.out.format("Generate pixel map for Layer %d %s\n", layer.id(), layer.name());
                System.out.format("------------------------\n");
            }

            int found=0;
            Vector3d downversor   = null;
            Vector3d rightversor  = null;
            Vector3d vertex       = null;
            for(int ifa=0; ifa<compo_plane.size(); ifa++){
                if(compo_list.get(ifa)==ico){
                    if(debugMode>=1){ System.out.format("  --> ifa %4d ", ifa); dump_Face( compo_plane.face(ifa) ); }
                    if(found==0){
                        Vector3d vp0 = toVector3d( compo_plane.face(ifa).point(0) );
                        Vector3d vp1 = toVector3d( compo_plane.face(ifa).point(1) );
                        Vector3d vp2 = toVector3d( compo_plane.face(ifa).point(2) );
                        downversor   = (vp0.minus(vp1)).normalized();
                        rightversor  = (vp2.minus(vp1).normalized());
                        vertex       = new Vector3d(vp1);
                        if(debugMode>=1){
                            System.out.format("MAPMT ico %4d  ifa %4d \n",ico, ifa);
                            System.out.format("vtx0  %s \n",toString(vp0));
                            System.out.format("vtx1  %s \n",toString(vp1));
                            System.out.format("vtx2  %s \n",toString(vp2));
                            System.out.format("down  %s \n",toString(downversor));
                            System.out.format("right %s \n",toString(rightversor));
                        }
                        found++;
                    }
                }
            }

            if(downversor!=null && rightversor!= null) {
                pmtpixels = new RICHPixel(new Vector3d(0.,0.,0.), downversor, rightversor);
                if(debugMode>=1){
                    pmtpixels.show_Pixels( vertex );
                    vertex = toVector3d( layer.get_CompoFace(5,0).point(1) );
                    pmtpixels.show_Pixels( vertex );
                    vertex = toVector3d( layer.get_CompoFace(363,0).point(1) );
                    pmtpixels.show_Pixels( vertex );
                    vertex = toVector3d( layer.get_CompoFace(390,0).point(1) );
                    pmtpixels.show_Pixels( vertex );
                }
            }
        }
    }
 

    //------------------------------
    public Shape3D generate_Nominal_Plane(RICHLayer layer, int ico){
    //------------------------------

        int debugMode = 0;

        int ilay = layer.id();

        Point3D extre1 = new Point3D(0.0, 0.0, 0.0);
        Point3D extre2 = new Point3D(0.0, 0.0, 0.0);
        Point3D extre3 = new Point3D(0.0, 0.0, 0.0);
        Point3D extre4 = new Point3D(0.0, 0.0, 0.0);

        //  Aerogel 2cm plane within B1 mirror 
        if(ilay==RICHLayerType.AEROGEL_2CM_B1.id() && ico==0){
            extre1 = new Point3D(-41.1598, 11.5495, 588.125);
            extre2 = new Point3D(-41.1598, -11.5495, 588.125);
            extre3 = new Point3D(-110.583, 51.6008, 555.752);
            extre4 = new Point3D(-110.583, -51.6008, 555.752);
        }

        //  Aerogel 2cm plane within B2 mirror
        if(ilay==RICHLayerType.AEROGEL_2CM_B2.id() && ico==0){
            extre1 = new Point3D(-111.353, 53.6256, 555.393);
            extre2 = new Point3D(-111.353, -53.6256, 555.393);
            extre3 = new Point3D(-165.777, 85.0457, 530.015);
            extre4 = new Point3D(-165.777, -85.0457, 530.015);
        }

        //  Aerogel 6cm plane within CFRP panel
        if((ilay==RICHLayerType.AEROGEL_3CM_L1.id() || ilay==RICHLayerType.AEROGEL_3CM_L2.id()) && ico==0){
            extre1 = new Point3D(-167.565, 85.356, 530.003);
            extre2 = new Point3D(-167.565, -85.356, 530.003);
            extre3 = new Point3D(-240.137, 127.254, 496.162);
            extre4 = new Point3D(-240.137, -127.254, 496.162);
        }

        // Front mirror
        if((ilay==5 || ilay==6) && ico==0){
            extre1 = new Point3D(-37.165,  11.847,  587.781);
            extre2 = new Point3D(-37.165,  -11.847,  587.781);
            extre3 = new Point3D(-165.136,  85.728,  528.107);
            extre4 = new Point3D(-165.136,  -85.728,  528.107);
        }

        // Left-side mirror
        if((ilay==7 || ilay==7) && ico==0){
            extre1 = new Point3D(-39.849,  12.095,  688.630);
            extre2 = new Point3D(-39.849,  12.095,  591.568);
            extre3 = new Point3D(-238.031, 126.515,  526.116);
            extre4 = new Point3D(-229.924, 121.834,  502.935);
        }

        // Right-side mirror
        if((ilay==8 || ilay==9) && ico==0){
            extre1 = new Point3D(-39.849,  -12.095,  688.630);
            extre2 = new Point3D(-39.849,  -12.095,  591.568);
            extre3 = new Point3D(-238.031, -126.515,  526.116);
            extre4 = new Point3D(-229.924, -121.834,  502.935);
        }

        // Bottom mirror
        if(ilay==RICHLayerType.MIRROR_BOTTOM.id() && ico==0){
            extre1 = new Point3D(-39.763,  11.500,  591.601);
            extre2 = new Point3D(-39.763,  -11.500,  591.601);
            extre3 = new Point3D(-39.763,  11.500,  687.101);
            extre4 = new Point3D(-39.763,  -11.500,  687.101);
        }

        //  Spherical mirror
        if(ilay==RICHLayerType.MIRROR_SPHERE.id()){
            if(ico==0){
                extre1 = new Point3D(-146.861, 77.9926, 629.86);
                extre2 = new Point3D(-146.861, -77.9926, 629.86);
                extre3 = new Point3D(-244.481, 134.353, 516.032);
                extre4 = new Point3D(-244.481, -134.353, 516.032);
            }
            if(ico==1){
                extre1 = new Point3D(-186.669, 100.976, 598.990);
                extre2 = new Point3D(-195.823,  42.177, 612.446);
                extre3 = new Point3D(-146.869,  77.996, 629.870);
                extre4 = new Point3D(-150.160,  40.583, 637.633);
            }
            if(ico==2){
                extre1 = new Point3D(-186.670,-100.975, 598.991);
                extre2 = new Point3D(-195.760, -42.186, 612.450);
                extre3 = new Point3D(-146.862, -77.995, 629.860);
                extre4 = new Point3D(-150.160, -40.592, 637.625);
            }
            if(ico==3){
                extre1 = new Point3D(-244.480, 134.356, 516.045);
                extre2 = new Point3D(-219.293, 119.805, 560.660);
                extre3 = new Point3D(-267.718,  66.825, 530.562);
                extre4 = new Point3D(-232.817,  69.694, 573.835);
            }
            if(ico==4){
                extre1 = new Point3D(-244.475,-134.350, 516.040);
                extre2 = new Point3D(-219.291,-119.808, 560.655);
                extre3 = new Point3D(-267.713, -66.825, 530.556);
                extre4 = new Point3D(-232.817, -69.699, 573.836);
            }
            if(ico==5){
                extre1 = new Point3D(-150.187, -40.275, 637.670);
                extre2 = new Point3D(-195.858, -41.878, 612.487);
                extre3 = new Point3D(-150.187,  40.268, 637.668);
                extre4 = new Point3D(-195.842,  41.844, 612.500);
            }
            if(ico==6){
                extre1 = new Point3D(-239.371,   0.150, 580.221);
                extre2 = new Point3D(-274.840,   0.150, 535.006);
                extre3 = new Point3D(-232.873,  69.405, 573.896);
                extre4 = new Point3D(-267.781,  66.526, 530.596);
            }
            if(ico==7){
                extre1 = new Point3D(-239.371,  -0.150, 580.221);
                extre2 = new Point3D(-274.840,  -0.150, 535.010);
                extre3 = new Point3D(-232.873, -69.404, 573.889);
                extre4 = new Point3D(-267.782, -66.530, 530.594);
            }
            if(ico==8){
                extre1 = new Point3D(-236.779,  42.186, 578.135);
                extre2 = new Point3D(-196.078,  42.180, 612.277);
                extre3 = new Point3D(-219.115, 119.693, 560.915);
                extre4 = new Point3D(-186.889, 101.102, 598.779);
            }
            if(ico==9){
                extre1 = new Point3D(-236.810,  41.877, 578.175);
                extre2 = new Point3D(-196.108,  41.835, 612.303);
                extre3 = new Point3D(-236.818, -41.883, 578.157);
                extre4 = new Point3D(-196.105, -41.883, 612.315);
            }
            if(ico==10){
                extre1 = new Point3D(-236.785, -42.182, 578.124);
                extre2 = new Point3D(-196.080, -42.185, 612.272);
                extre3 = new Point3D(-219.114,-119.708, 560.905);
                extre4 = new Point3D(-186.891,-101.097, 598.779);
            }
        }

        // MA-PMTs
        if(ilay==RICHLayerType.MAPMT.id()  && ico==0){
            extre1 = new Point3D(-41.126,  15.850,  694.974);
            extre2 = new Point3D(-41.126,  -15.850,  694.974);
            extre3 = new Point3D(-151.514,  74.150,  643.499);
            extre4 = new Point3D(-151.514,  -74.150,  643.499);
        }

        /*
        *  force the layer orientation 
        */
        Vector3D vinside = layer.get_Vinside();

        Triangle3D half1 = new Triangle3D( extre1, extre2, extre3);
        Triangle3D half2 = new Triangle3D( extre2, extre4, extre3);
        Vector3D norm1 = half1.normal().asUnit();
        Vector3D norm2 = half2.normal().asUnit();
        Shape3D guess_one = new Shape3D(half1, half2);
        Vector3D norm_one = half1.normal().asUnit();
        double ang_one = norm_one.angle(vinside)*RICHGeoConstants.RAD;

        Triangle3D half3 = new Triangle3D( extre3, extre2, extre1);
        Triangle3D half4 = new Triangle3D( extre3, extre4, extre2);
        Vector3D norm3 = half3.normal().asUnit();
        Vector3D norm4 = half4.normal().asUnit();
        Shape3D guess_two = new Shape3D(half3, half4);
        Vector3D norm_two = half3.normal().asUnit();
        double ang_two = norm_two.angle(vinside)*RICHGeoConstants.RAD;

        if(debugMode>=1){
            System.out.format("Look for Nominal plane %3d ico %3d\n",ilay,ico);
            System.out.format("norm1 %s \n",norm1.toStringBrief(3));
            System.out.format("norm2 %s \n",norm2.toStringBrief(3));
            System.out.format("norm3 %s \n",norm3.toStringBrief(3));
            System.out.format("norm4 %s \n",norm4.toStringBrief(3));
            guess_one.show();
            System.out.format("Guess one normal %s --> %7.2f \n",norm_one.toStringBrief(3), ang_one);
            guess_two.show();
            System.out.format("Guess two normal %s --> %7.2f \n",norm_two.toStringBrief(3), ang_two);
        }

        if(ang_one<30){
            if(debugMode>=1)System.out.format(" --> guess one\n");
            return guess_one;
        }else{
            if(debugMode>=1)System.out.format(" --> guess two\n");
            return guess_two;
        }

    }


    //------------------------------
    public int Maroc2Anode(int channel) {
    //------------------------------

        // return anode from MAROC channel
        return RICHGeoConstants.anode_map[(channel)%64];
    }

    //------------------------------
    public int Tile2PMT(int tile, int channel) {
    //------------------------------

        // return anode from MAROC channel

        return RICHGeoConstants.tile2pmt[tile-1][(int) (channel-1)/64];
    }


    //------------------------------
    public int get_LayerNumber(int isec, String slay){
    //------------------------------
        int debugMode = 0;

        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return -1;

        for (int ila=0; ila<richlayers.get(irich-1).size(); ila++){
            if(richlayers.get(irich-1).get(ila).name().equals(slay)) {
                if(debugMode>=1)System.out.format(" Find layer %s --> %4d \n",slay,ila);
                return ila;
            }
        }  
        return -1;
    }


    //------------------------------
    public RICHLayer get_Layer(int isec, String slay){
    //------------------------------

        int debugMode = 0;

        int irich = geocal.find_RICHModule(isec);
        if(debugMode>=1)System.out.format("get_Layer %4d %4d %s \n", irich, isec, slay);
        if(irich==0) return null;

        for (int ila=0; ila<richlayers.get(irich-1).size(); ila++){
            if(debugMode>=1)System.out.format(" --> %4d %4d %4d %s \n", irich, isec, ila, richlayers.get(irich-1).get(ila).name());
            if(richlayers.get(irich-1).get(ila).name().equals(slay)) {
                return richlayers.get(irich-1).get(ila);
            }
        }  
        return null;
    }


    //------------------------------
    public RICHLayer get_Layer(int isec, int ilay){ 
    //------------------------------

        int debugMode = 0;

        int irich = geocal.find_RICHModule(isec);
        if(debugMode>=1)System.out.format("get_Layer %4d %4d %4d \n", irich, isec, ilay);
        if(irich==0) return null;

        if(ilay>-1 && ilay<NLAY) return richlayers.get(irich-1).get(ilay);
        return null; 
    }


    //------------------------------
    public RICHComponent get_RICHGeant4Component(int isec, int ilay, int idgea, int ico){ 
    //------------------------------

        if(idgea==RICHLayerType.MAPMT.ccdb()) return new RICHComponent(isec, ico, idgea, 1, richfactory.GetPhotocatode(ico+1));

        if(idgea==RICHLayerType.AEROGEL_2CM_B1.ccdb() || idgea==RICHLayerType.AEROGEL_2CM_B2.ccdb()  || 
           idgea==RICHLayerType.AEROGEL_3CM_L1.ccdb() || idgea==RICHLayerType.AEROGEL_3CM_L2.ccdb()  || 
           idgea==RICHLayerType.MIRROR_BOTTOM.ccdb()  || idgea==RICHLayerType.MIRROR_SPHERE.ccdb() ) 
                  return new RICHComponent(isec, ilay, ico, 1, richfactory.getStlComponent(idgea, ico));

        return null;
    }


    //------------------------------
    public int get_RICHFactory_Size(int idgea){ 
    //------------------------------
        if(idgea==RICHLayerType.MAPMT.ccdb()) return RICHGeoConstants.NPMT;
        if(idgea==RICHLayerType.AEROGEL_2CM_B1.ccdb() || idgea==RICHLayerType.AEROGEL_2CM_B2.ccdb()  || 
           idgea==RICHLayerType.AEROGEL_3CM_L1.ccdb() || idgea==RICHLayerType.AEROGEL_3CM_L2.ccdb()  || 
           idgea==RICHLayerType.MIRROR_BOTTOM.ccdb()  || idgea==RICHLayerType.MIRROR_SPHERE.ccdb() ) 
                  {return richfactory.getStlNumber(idgea);}

        return 0;
    }


    //------------------------------
    public RICHComponent get_Component(int isec, int ilay, int ico){ 
    //------------------------------

        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return null;

        return richlayers.get(irich-1).get(ilay).get(ico);
    }


    //------------------------------
    public CSG get_CSGVolume(int isec, int ilay, int ico){
    //------------------------------

        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return null;

        return richlayers.get(irich-1).get(ilay).get(ico).get_CSGVol();
    }

     //------------------------------
     public ArrayList<CSG> get_CSGLayerVolumes(int isec, int ilay){
     //------------------------------

        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return null;

        // ATT: get_CSGVolume should become a Layer method
        ArrayList<CSG> vols = new ArrayList<CSG>();
        RICHLayer layer = richlayers.get(irich-1).get(ilay);
        for (int ico=0; ico<layer.size(); ico++){
            CSG vol = get_CSGVolume(isec, ilay, ico);
            if(vol!=null)vols.add(vol);
        }  
        return vols;
     }


     //------------------------------
     public G4Stl get_StlVolume(int isec, int ilay, int ico){
     //------------------------------

        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return null;

        RICHComponent compo = richlayers.get(irich-1).get(ilay).get(ico);
        if(compo.get_voltype()==2) return compo.get_StlVol();
        return null;
     }


     //------------------------------
     public ArrayList<G4Stl> get_StlLayerVolumes(int isec, int ilay){
     //------------------------------

        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return null;

        ArrayList<G4Stl> vols = new ArrayList<G4Stl>();
        RICHLayer layer = richlayers.get(irich-1).get(ilay);
        for (int ico=0; ico<layer.size(); ico++){
            G4Stl vol = get_StlVolume(isec, ilay, ico);
            if(vol!=null)vols.add(vol);
        }  
        return vols;
     }


     //------------------------------
     public G4Box get_BoxVolume(int isec, int ilay, int ico){
     //------------------------------

        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return null;

        RICHComponent compo = richlayers.get(irich-1).get(ilay).get(ico);
        if(compo.get_voltype()==1) return compo.get_BoxVol();
        return null;
     }


     //------------------------------
     public ArrayList<G4Box> get_BoxLayerVolumes(int isec, int ilay){
     //------------------------------
 
        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return null;

        ArrayList<G4Box> vols = new ArrayList<G4Box>();
        RICHLayer layer = richlayers.get(irich-1).get(ilay);
        for (int ico=0; ico<layer.size(); ico++){
            G4Box vol = get_BoxVolume(isec, ilay, ico);
            if(vol!=null)vols.add(vol);
        }  
        return vols;
     }


     //------------------------------
     public Vector3D toVector3D(Vector3d vin) {
     //------------------------------
        Vector3D vout = new Vector3D(vin.x, vin.y, vin.z); 
	return vout;
     }


     //------------------------------
     public Vector3D toVector3D(Point3D pin) {
     //------------------------------
        Vector3D vout = new Vector3D(pin.x(), pin.y(), pin.z()); 
	return vout;
     }


     //------------------------------
     public Vector3d toVector3d(Vertex ver) {return  new Vector3d(ver.pos.x, ver.pos.y, ver.pos.z); }
     //------------------------------


     //------------------------------
     public Vector3d toVector3d(Vector3D vin) {
     //------------------------------
        Vector3d vout = new Vector3d(vin.x(), vin.y(), vin.z()); 
	return vout;
     }

     //------------------------------
     public Vector3d toVector3d(Point3D pin) {
     //------------------------------
        Vector3d vout = new Vector3d(pin.x(), pin.y(), pin.z()); 
	return vout;
     }

     //------------------------------
     public Point3D toPoint3D(Vertex vin) {
     //------------------------------
        Point3D pout = new Point3D(vin.pos.x, vin.pos.y, vin.pos.z); 
	return pout;
     }

     //------------------------------
     public Point3D toPoint3D(Vector3D vin) {
     //------------------------------
        Point3D pout = new Point3D(vin.x(), vin.y(), vin.z()); 
	return pout;
     }


     //------------------------------
     public Point3D toPoint3D(Vector3d vin) {
     //------------------------------
        if(vin==null) return null;
        Point3D pout = new Point3D(vin.x, vin.y, vin.z); 
	return pout;
     }


     //------------------------------
     public Line3d toLine3d(Line3D lin) {
     //------------------------------
        Line3d lout = new Line3d(toVector3d(lin.origin()), toVector3d(lin.end()));
	return lout;
     }


     //------------------------------
     public Line3D toLine3D(Line3d lin) {
     //------------------------------
        Line3D lout = new Line3D(toPoint3D(lin.origin()), toPoint3D(lin.end()));
        return lout;
     }

    //------------------------------
    public void translate_Triangle3D(Triangle3D tri, Vector3d shift) {
    //------------------------------

        tri.translateXYZ(shift.x, shift.y, shift.z);

    }

    //------------------------------
    public void translate_Sphere3D(Sphere3D sphere, Vector3D shift) { sphere.translateXYZ(shift.x(), shift.y(), shift.z()); }
    //------------------------------

    //------------------------------
    public void translate_Shape3D(Shape3D shape, Vector3D shift) { shape.translateXYZ(shift.x(), shift.y(), shift.z()); }
    //------------------------------

    //------------------------------
    public void translate_Sphere3D(Sphere3D sphere, Vector3d shift) { sphere.translateXYZ(shift.x, shift.y, shift.z); }
    //------------------------------

    //------------------------------
    public void translate_Shape3D(Shape3D shape, Vector3d shift) { shape.translateXYZ(shift.x, shift.y, shift.z); }
    //------------------------------

    //------------------------------
    public void rotate_Triangle3D(Triangle3D tri, Vector3d angle) {
    //------------------------------

        Vector3d bary = get_Triangle3D_Bary(tri);

        tri.rotateZ(angle.z);
        tri.rotateY(angle.y);
        tri.rotateX(angle.x);

        Vector3d shift = bary.minus( get_Triangle3D_Bary(tri) );
        translate_Triangle3D(tri, shift);

    }


    // ----------------
    public void show_RICH(String name, String head){
    // ----------------

        System.out.format(" -----------------------\n  %s \n ----------------------- \n", name);

        for(int irich=1; irich<=geocal.nRICHes(); irich++){

            int isec = geocal.find_RICHSector(irich);
            if(isec==0) continue;

            for (int ilay=0; ilay<NLAY; ilay++){
                String ini = head + " "+ ilay;
                RICHLayer layer = get_Layer(isec, ilay);
                if(layer.is_aerogel() || layer.is_mapmt()){
                    show_Shape3D(layer.get_GlobalSurf(), null, ini);
                    if(layer.is_aerogel()){
                        show_Shape3D(layer.get_TrackingSurf(), null, "AA");
                        for(int ico=0; ico<layer.size(); ico++) System.out.format("HH %4d %4d %s \n", ilay, ico, layer.get_CompoBary(ico).toStringBrief(2));
                    }
                    if(layer.is_mapmt())show_Shape3D(layer.get_TrackingSurf(), null, "PP");

                }else{
                    if(layer.is_spherical_mirror()) show_Shape3D(layer.get_GlobalSurf(), null, ini);
                    show_Shape3D(layer.get_TrackingSurf(), null, ini);
                }
            }
        } 
    }


    // ----------------
    public void show_Triangle3D(Triangle3D tri, String name){
    // ----------------

        if(name!=null) System.out.format(" %s ----------------------- %s \n", name, toString(get_Triangle3D_Bary(tri)));
        System.out.format(" %s %s %s \n", toString(tri.point(0)), toString(tri.point(1)), toString(tri.point(2)));
    }


    // ----------------
    public void show_Shape3D(Shape3D plane, String name, String head){
    // ----------------

        if(name!=null) System.out.format(" %s ----------------------- %s \n", name, toString(get_Shape3D_Bary(plane)));
        for (int ifa=0; ifa<plane.size(); ifa++){
            Face3D f = plane.face(ifa);
            if(head==null){
                System.out.format(" %s %s %s \n", toString(f.point(0)), toString(f.point(1)), toString(f.point(2)));
            }else{
                System.out.format(" %s %s %s %s \n", head, toString(f.point(0)), toString(f.point(1)), toString(f.point(2)));
            }
        }
    }


    // ----------------
    public void show_Sphere3D(Sphere3D sphere, String name, String head){
    // ----------------

        if(name!=null) System.out.format(" %s ----------------------- \n", name);
        if(head==null){
            System.out.format(" %s %7.2f \n", toString(sphere.getCenter()), sphere.getRadius());
        }else{
            System.out.format(" %s %s %7.2f \n", head, toString(sphere.getCenter()), sphere.getRadius());
        }
    }


    //------------------------------
    public Vector3D into_LabFrame(Vector3D vec, RICHFrame frame) {
    //------------------------------

        return into_LabFrame(vec, frame.xref(), frame.yref(), frame.zref());

    }

    //------------------------------
    public Vector3D into_LabFrame(Vector3D vec, Vector3D xref, Vector3D yref, Vector3D zref) {
    //------------------------------

        // decompose each vector/rotation along a ref axis (i.e. angle.x*xref) into three cartesian rotation in the lab system 
        return new Vector3D( vec.z()*zref.x() + vec.y()*yref.x() + vec.x()*xref.x(),
                             vec.z()*zref.y() + vec.y()*yref.y() + vec.x()*xref.y(),
                             vec.z()*zref.z() + vec.y()*yref.z() + vec.x()*xref.z());
    }


    //------------------------------
    public void align_Element(Shape3D shape, RICHFrame frame, Vector3D angle, Vector3D shift) {
    //------------------------------

        int debugMode = 0;

        if(shape!=null){

            if(debugMode>=1)System.out.format(" FRAME %s %s %s %s\n",frame.xref().toStringBrief(2),frame.yref().toStringBrief(2),
                                                     frame.zref().toStringBrief(2),frame.bref().toStringBrief(2));
            if(debugMode>=1)show_Shape3D(shape, "BEFORE", null);

            if(angle.mag()>0){
                translate_Shape3D(shape, frame.bref().multiply(-1.0));

                Vector3D ang_lab = into_LabFrame(angle, frame);
                if(debugMode>=1)System.out.format(" ang_lab %s \n", ang_lab.toStringBrief(2));
                shape.rotateZ(ang_lab.z());
                shape.rotateY(ang_lab.y());
                shape.rotateX(ang_lab.x());

                translate_Shape3D(shape, frame.bref());
            }

            if(shift.mag()>0){
                Vector3D shift_lab = into_LabFrame(shift, frame);
                if(debugMode>=1)System.out.format(" shift_lab %s \n", shift_lab.toStringBrief(2));
                translate_Shape3D(shape, shift_lab);
            }

            if(debugMode>=1)show_Shape3D(shape, "AFTER ", null);
        }

    }


    //------------------------------
    public void align_Element(Sphere3D sphere, RICHFrame frame, Vector3D angle, Vector3D shift) {
    //------------------------------

        int debugMode = 0;

        if(sphere!=null){

            if(debugMode>=1) System.out.format(" FRAME %s %s %s %s\n",frame.xref().toStringBrief(2),frame.yref().toStringBrief(2),
                                                     frame.zref().toStringBrief(2),frame.bref().toStringBrief(2));
            if(debugMode>=1)show_Sphere3D(sphere, "BEFORE", null);

            if(angle.mag()>0){
                translate_Sphere3D(sphere, frame.bref().multiply(-1.0));

                Vector3D ang_lab = into_LabFrame(angle, frame);
                if(debugMode>=1)System.out.format(" ang_lab %s \n", ang_lab.toStringBrief(2));
                sphere.rotateZ(ang_lab.z());
                sphere.rotateY(ang_lab.y());
                sphere.rotateX(ang_lab.x());

                translate_Sphere3D(sphere, frame.bref());
            }
            
            if(shift.mag()>0){
                Vector3D shift_lab = into_LabFrame(shift, frame);
                if(debugMode>=1)System.out.format(" shift_lab %s \n", shift_lab.toStringBrief(2));
                translate_Sphere3D(sphere, shift_lab);
            }

            if(debugMode>=1)show_Sphere3D(sphere, "AFTER ", null);
        }

    }

    //------------------------------
    public Shape3D copy_Shape3D(Shape3D shape) { 
    //------------------------------

        Shape3D copy = new Shape3D();
        for (int ifa=0; ifa<shape.size(); ifa++){copy.addFace( toTriangle3D(shape.face(ifa)));}
        return copy;

    }

    // ----------------
    public void merge_Shape3D(Shape3D shape, Shape3D other) {
    // ----------------

        for(int ifa=0; ifa<other.size(); ifa++)shape.addFace( other.face(ifa) );

    }


    //------------------------------
    public Vector3d get_Shape3D_Center(Shape3D shape) { return toVector3d(shape.center()); }
    //------------------------------

    
    // ----------------
    public Vector3d get_CSGBary(CSG CSGVol) {
    // ----------------

        /*
        *   Avoid double counting of points  
        */
        int debugMode = 0;
        List<Vector3d> pts = new ArrayList<Vector3d>();
        if(debugMode>=1)System.out.format(" get_CSGBary %d \n", CSGVol.getPolygons().size());

        double cX=0.0;
        double cY=0.0;
        double cZ=0.0;
        double np=0.0;
        int ii=0;
        for (Polygon pol: CSGVol.getPolygons()){
            if(debugMode>=1)System.out.format(" poli  %4d ",ii);
            for (Vertex vert: pol.vertices ){
                Vector3d p = toVector3d(vert);
                int found = 0;
                for(int i=0; i<pts.size(); i++){
                    if(p.distance(pts.get(i))<1.e-3)found=1;
                }

                if(found==0){
                    if(debugMode>=1)System.out.format(" --> New Vertex %s\n",toString(p));
                    pts.add(p);
                    cX += p.x;
                    cY += p.y;
                    cZ += p.z;
                    np += 1;
                }else{
                    if(debugMode>=1)System.out.format(" --> Old Vertex %s\n",toString(p));
                }
                
            }
            ii++;
        } 

        if(np>0)return new Vector3d(cX/np, cY/np, cZ/np);
        return new Vector3d(0., 0., 0.);
    }


    //------------------------------
    public Vector3d get_Shape3D_Bary(Shape3D shape) { 
    //------------------------------
     
        /*
        *   Avoid double counting of points  
        */
        int debugMode = 0;
        List<Vector3d> pts = new ArrayList<Vector3d>();
        if(debugMode>=1)System.out.format(" get_Shape3D_Bary %d \n", shape.size());

        double cX=0.0;
        double cY=0.0;
        double cZ=0.0;
        double np=0.0;
        for (int ifa=0; ifa<shape.size(); ifa++){
            Face3D f = shape.face(ifa);
            if(debugMode>=1)System.out.format(" --> get_face %d \n",ifa);
            for (int ipo=0; ipo<3; ipo++){

                Vector3d p = toVector3d(f.point(ipo));
                int found = 0;
                for(int i=0; i<pts.size(); i++){
                    if(p.distance(pts.get(i))<1.e-3)found=1;
                }

                if(found==0){
                    if(debugMode>=1)System.out.format(" --> New Vertex %s\n",toString(p));
                    pts.add(p);
                    cX += p.x;
                    cY += p.y;
                    cZ += p.z;
                    np += 1;
                }else{
                    if(debugMode>=1)System.out.format(" --> Old Vertex %s\n",toString(p));
                }
                
            }
        } 

        if(np>0)return new Vector3d(cX/np, cY/np, cZ/np);
        return new Vector3d(0., 0., 0.);
    }


    //------------------------------
    public Vector3d get_Triangle3D_Bary(Triangle3D tri) { return toVector3d(tri.center()); }
    //------------------------------


    //------------------------------
    public Vector3d get_Shape3D_Normal(Shape3D shape, int iface) {
    //------------------------------

        Triangle3D face = new Triangle3D(shape.face(iface).point(0), shape.face(iface).point(1), shape.face(iface).point(2));
        Vector3D normal = face.normal();
        return toVector3d(normal).normalized(); 

    }


    //------------------------------
    public Vector3d get_Shape3D_Normal(Shape3D shape) {
    //------------------------------

        Triangle3D face = new Triangle3D(shape.face(0).point(0), shape.face(0).point(1), shape.face(0).point(2));
        Vector3D normal = face.normal();
        return toVector3d(normal).normalized(); 

    }


    //------------------------------
    public Vector3d get_Poly_Normal(Polygon pol) {
    //------------------------------
        Vector3d a = pol.vertices.get(0).pos;
        Vector3d b = pol.vertices.get(1).pos;
        Vector3d c = pol.vertices.get(2).pos;
        Vector3d n = b.minus(a).cross(c.minus(a)).normalized();
        return n;
    }


    //------------------------------
    public Vector3d get_Poly_Bary(Polygon pol) {
    //------------------------------
        Vector3d a = pol.vertices.get(0).pos;
        Vector3d b = pol.vertices.get(1).pos;
        Vector3d c = pol.vertices.get(2).pos;
        Vector3d bary = a.plus(b);
        bary = bary.add(c);
        return bary.dividedBy(3.);
    }

    //------------------------------
    public double get_Poly_Area(Polygon pol) {
    //------------------------------
        Vector3d a = pol.vertices.get(0).pos;
        Vector3d b = pol.vertices.get(1).pos;
        Vector3d c = pol.vertices.get(2).pos;
        Line3D base = new Line3D(toPoint3D(a), toPoint3D(b));
        Line3D h = base.distance( toPoint3D(c));
        return base.length()*h.length()/2;
    }

    
    // ----------------
    public String get_PlaneMirrorSide(RICHComponent compo) {
    // ----------------

        int debugMode = 0;

        Vector3D front   = new Vector3D(-0.42,   0.00,   0.91);
        Vector3D left    = new Vector3D(-0.50,  -0.87,   0.00);
        Vector3D right   = new Vector3D(-0.50,   0.87,   0.00);
        Vector3D bottom  = new Vector3D(-1.00,   0.00,   0.00);

        //ATT: this is before having set the layer components
        Vector3D bary = toVector3D(get_CSGBary( compo.get_CSGVol() ));
        if(debugMode>=1)System.out.format(" compo bary %s \n", toString(bary));

        for (Triangle3D pol: toTriangle3D(compo.get_CSGVol().getPolygons()) ){

            if(debugMode>=1)System.out.format("Test front %7.3f  left %7.3f  right %7.3f  bot %7.3f \n",
                     pol.normal().angle(front), pol.normal().angle(left),
                     pol.normal().angle(right), pol.normal().angle(bottom));

            if(pol.normal().angle(front)<5.e-3){
                 if(bary.x() > -100){
                     return new String("MIRROR_FRONT_B1");
                 }else{
                     return new String("MIRROR_FRONT_B2");
                 }
            }
            if(pol.normal().angle(left)<5.e-3){
                 if(bary.x() > -100){
                     return new String("MIRROR_LEFT_L1");
                 }else{
                     return new String("MIRROR_LEFT_L2");
                 }
            }
            if(pol.normal().angle(right)<5.e-3){
                 if(bary.x() > -100){
                     return new String("MIRROR_RIGHT_R1");
                 }else{
                     return new String("MIRROR_RIGHT_R2");
                 }
            }
            if(pol.normal().angle(bottom)<5.e-3){
                 return new String("MIRROR_BOTTOM");
            }
            
        }
        return new String("UNDEFINED");
    }


    // ----------------
    public void dump_Face(Face3D face) {
    // ----------------

            Vector3d p0 = toVector3d( face.point(0) );
            Vector3d p1 = toVector3d( face.point(1) );
            Vector3d p2 = toVector3d( face.point(2) );
            System.out.format(" %8.3f %8.3f %8.3f   %8.3f %8.3f %8.3f  %8.3f %8.3f %8.3f \n",p0.x,p0.y,p0.z,p1.x,p1.y,p1.z,p2.x,p2.y,p2.z);
    }


    // ----------------
    public void dump_Polygon(Polygon pol) {
    // ----------------
        for (Vertex vert: pol.vertices ){
            System.out.format(" %7.2f %7.2f %7.2f  ",vert.pos.x,vert.pos.y,vert.pos.z);
        }
        System.out.format(" Norm %7.2f %7.2f %7.2f A %7.2f \n",get_Poly_Normal(pol).x,get_Poly_Normal(pol).y,get_Poly_Normal(pol).z,get_Poly_Area(pol));
    }

    // ----------------
    public void dump_StlComponent(CSG CSGVol) {
    // ----------------

        System.out.format(" ------------------\n");
        System.out.format(" Dump of Stl \n");
        System.out.format(" ------------------\n");
        int ii=0;
        for (Polygon pol: CSGVol.getPolygons()){
            System.out.format("  %4d ",ii);
            for (Vertex vert: pol.vertices ){
                System.out.format(" Vtx %7.2f %7.2f %7.2f  ",vert.pos.x,vert.pos.y,vert.pos.z);
            }
            System.out.format(" Norm %7.2f %7.2f %7.2f A %7.2f \n",get_Poly_Normal(pol).x,get_Poly_Normal(pol).y,get_Poly_Normal(pol).z,get_Poly_Area(pol));
            ii++;
        }

    }


    // ----------------
    public void dump_StlComponent(int isec, int ilay, int ico) {
    // ----------------

        System.out.format(" ------------------\n");
        System.out.format(" Dump of Stl %d in layer %d \n", ico, ilay);
        System.out.format(" ------------------\n");
        int ii=0;
        for (Polygon pol: get_CSGVolume(isec, ilay, ico).getPolygons()){
            System.out.format("  %4d ",ii);
            for (Vertex vert: pol.vertices ){
                System.out.format(" Vtx %7.2f %7.2f %7.2f  ",vert.pos.x,vert.pos.y,vert.pos.z);
            }
            System.out.format(" Norm %7.2f %7.2f %7.2f A %7.2f \n",get_Poly_Normal(pol).x,get_Poly_Normal(pol).y,get_Poly_Normal(pol).z,get_Poly_Area(pol));
            ii++;
        }

    }


    // ----------------
    public Point3D find_IntersectionSpheMirror(int isec, Line3D ray){
    // ----------------

        int debugMode = 0;
        RICHIntersection inter = get_Layer(isec, "MIRROR_SPHERE").find_Entrance(ray, -1);

        if(inter!=null){
            if(debugMode>=1)  System.out.format("find_intersection with SPHERICAL (%d, %d): %s\n",
                 inter.get_layer(), inter.get_component(), inter.get_pos().toStringBrief(2));
            return inter.get_pos();
        }else{
            if(debugMode>=1)  System.out.format("find NO intersection with SPHERICAL \n");
        }

        return null;

    }

    // ----------------
    public Point3D find_IntersectionMAPMT(int isec, Line3D ray){
    // ----------------

        int debugMode = 0;

        RICHIntersection inter = get_Layer(isec, "MAPMT").find_Entrance(ray, -1);

        if(inter!=null){
            if(debugMode>=1)  System.out.format("find_intersection with MAPMT (%d, %d): %s\n",
                 inter.get_layer(), inter.get_component(), inter.get_pos().toStringBrief(2));
            return inter.get_pos();
        }

        return null;
    }


    // ----------------
    public boolean has_RICH(int isec){
    // ----------------

        if(geocal.find_RICHModule(isec)>0)return true;
        return false;
    }


    // ----------------
    public boolean is_Spherical_Mirror (int isec, int ilay){
    // ----------------

        int irich = geocal.find_RICHModule(isec);
        if(irich==0) return false;

        if(richlayers.get(irich-1).get(ilay).name().equals("mirror_sphere"))return true;
        return false;  

    }





}
