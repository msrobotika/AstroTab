package org.msrobotika.astrotab.satellites

// Vallado, Crawford, Hujsak, Kelso, AIAA 2006-6753; Java: Shawn E. Gano (2009).
// Port Kotlin AstroTab 2026. Uso libre con cita según FAQ oficial de CelesTrak.
// https://celestrak.org/publications/AIAA/2006-6753/ (fuente y condiciones en notices).

@Suppress("UNUSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "NAME_SHADOWING", "UNUSED_VALUE", "UNUSED_PARAMETER")
internal object Sgp4Math {
    const val pi = 3.14159265358979323846
    enum class Gravconsttype { wgs72old, wgs72, wgs84 }
    private fun dpper(input_e3: Double, input_ee2: Double, input_peo: Double, input_pgho: Double, input_pho: Double, input_pinco: Double, input_plo: Double, input_se2: Double, input_se3: Double, input_sgh2: Double, input_sgh3: Double, input_sgh4: Double, input_sh2: Double, input_sh3: Double, input_si2: Double, input_si3: Double, input_sl2: Double, input_sl3: Double, input_sl4: Double, input_t: Double, input_xgh2: Double, input_xgh3: Double, input_xgh4: Double, input_xh2: Double, input_xh3: Double, input_xi2: Double, input_xi3: Double, input_xl2: Double, input_xl3: Double, input_xl4: Double, input_zmol: Double, input_zmos: Double, input_inclo: Double, input_init: Char, input_ep: Double, input_inclp: Double, input_nodep: Double, input_argpp: Double, input_mp: Double, input_opsmode: Char): DoubleArray {
        var e3 = input_e3
        var ee2 = input_ee2
        var peo = input_peo
        var pgho = input_pgho
        var pho = input_pho
        var pinco = input_pinco
        var plo = input_plo
        var se2 = input_se2
        var se3 = input_se3
        var sgh2 = input_sgh2
        var sgh3 = input_sgh3
        var sgh4 = input_sgh4
        var sh2 = input_sh2
        var sh3 = input_sh3
        var si2 = input_si2
        var si3 = input_si3
        var sl2 = input_sl2
        var sl3 = input_sl3
        var sl4 = input_sl4
        var t = input_t
        var xgh2 = input_xgh2
        var xgh3 = input_xgh3
        var xgh4 = input_xgh4
        var xh2 = input_xh2
        var xh3 = input_xh3
        var xi2 = input_xi2
        var xi3 = input_xi3
        var xl2 = input_xl2
        var xl3 = input_xl3
        var xl4 = input_xl4
        var zmol = input_zmol
        var zmos = input_zmos
        var inclo = input_inclo
        var init = input_init
        var ep = input_ep
        var inclp = input_inclp
        var nodep = input_nodep
        var argpp = input_argpp
        var mp = input_mp
        var opsmode = input_opsmode

        
        

        
        var twopi: Double = 2.0 * pi;
        var alfdp: Double = 0.0;
var betdp: Double = 0.0;
var cosip: Double = 0.0;
var cosop: Double = 0.0;
var dalf: Double = 0.0;
var dbet: Double = 0.0;
var dls: Double = 0.0;
var f2: Double = 0.0;
var f3: Double = 0.0;
var pe: Double = 0.0;
var pgh: Double = 0.0;
var ph: Double = 0.0;
var pinc: Double = 0.0;
var pl: Double = 0.0;
var sel: Double = 0.0;
var ses: Double = 0.0;
var sghl: Double = 0.0;
var sghs: Double = 0.0;
var shll: Double = 0.0;
var shs: Double = 0.0;
var sil: Double = 0.0;
var sinip: Double = 0.0;
var sinop: Double = 0.0;
var sinzf: Double = 0.0;
var sis: Double = 0.0;
var sll: Double = 0.0;
var sls: Double = 0.0;
var xls: Double = 0.0;
var xnoh: Double = 0.0;
var zf: Double = 0.0;
var zm: Double = 0.0;
var zel: Double = 0.0;
var zes: Double = 0.0;
var znl: Double = 0.0;
var zns: Double = 0.0;

        
        zns = 1.19459e-5;
        zes = 0.01675;
        znl = 1.5835218e-4;
        zel = 0.05490;

        
        zm = zmos + zns * t;
        
        if(init == 'y')
        {
            zm = zmos;
        }
        zf = zm + 2.0 * zes * Math.sin(zm);
        sinzf = Math.sin(zf);
        f2 = 0.5 * sinzf * sinzf - 0.25;
        f3 = -0.5 * sinzf * Math.cos(zf);
        ses = se2 * f2 + se3 * f3;
        sis = si2 * f2 + si3 * f3;
        sls = sl2 * f2 + sl3 * f3 + sl4 * sinzf;
        sghs = sgh2 * f2 + sgh3 * f3 + sgh4 * sinzf;
        shs = sh2 * f2 + sh3 * f3;
        zm = zmol + znl * t;
        if(init == 'y')
        {
            zm = zmol;
        }
        zf = zm + 2.0 * zel * Math.sin(zm);
        sinzf = Math.sin(zf);
        f2 = 0.5 * sinzf * sinzf - 0.25;
        f3 = -0.5 * sinzf * Math.cos(zf);
        sel = ee2 * f2 + e3 * f3;
        sil = xi2 * f2 + xi3 * f3;
        sll = xl2 * f2 + xl3 * f3 + xl4 * sinzf;
        sghl = xgh2 * f2 + xgh3 * f3 + xgh4 * sinzf;
        shll = xh2 * f2 + xh3 * f3;
        pe = ses + sel;
        pinc = sis + sil;
        pl = sls + sll;
        pgh = sghs + sghl;
        ph = shs + shll;

        if(init == 'n')
        {
            pe = pe - peo;
            pinc = pinc - pinco;
            pl = pl - plo;
            pgh = pgh - pgho;
            ph = ph - pho;
            inclp = inclp + pinc;
            ep = ep + pe;
            sinip = Math.sin(inclp);
            cosip = Math.cos(inclp);

            
            
            
            
            
            
            
            
            
            if(inclp >= 0.2)
            {
                ph = ph / sinip;
                pgh = pgh - cosip * ph;
                argpp = argpp + pgh;
                nodep = nodep + ph;
                mp = mp + pl;
            }
            else
            {
                
                sinop = Math.sin(nodep);
                cosop = Math.cos(nodep);
                alfdp = sinip * sinop;
                betdp = sinip * cosop;
                dalf = ph * cosop + pinc * cosip * sinop;
                dbet = -ph * sinop + pinc * cosip * cosop;
                alfdp = alfdp + dalf;
                betdp = betdp + dbet;
                nodep = (nodep % twopi);
                
                
                if((nodep < 0.0) && (opsmode == 'a'))
                {
                    nodep = nodep + twopi;
                }
                xls = mp + argpp + cosip * nodep;
                dls = pl + pgh - pinc * nodep * sinip;
                xls = xls + dls;
                xnoh = nodep;
                nodep = Math.atan2(alfdp, betdp);
                
                
                if((nodep < 0.0) && (opsmode == 'a'))
                {
                    nodep = nodep + twopi;
                }
                if(Math.abs(xnoh - nodep) > pi)
                {
                    if(nodep < xnoh)
                    {
                        nodep = nodep + twopi;
                    }
                    else
                    {
                        nodep = nodep - twopi;
                    }
                }
                mp = mp + pl;
                argpp = xls - mp - cosip * nodep;
            }
        }   

        return doubleArrayOf(
                    ep, inclp, nodep, argpp, mp
                );

    
    }
    private fun dscom(input_epoch: Double, input_ep: Double, input_argpp: Double, input_tc: Double, input_inclp: Double, input_nodep: Double, input_np: Double, input_satrec: Sgp4Record): DoubleArray {
        var epoch = input_epoch
        var ep = input_ep
        var argpp = input_argpp
        var tc = input_tc
        var inclp = input_inclp
        var nodep = input_nodep
        var np = input_np
        var satrec = input_satrec

        
        var snodm: Double = 0.0;
var cnodm: Double = 0.0;
var sinim: Double = 0.0;
var cosim: Double = 0.0;
var sinomm: Double = 0.0;
var cosomm: Double = 0.0;
var day: Double = 0.0;
var em: Double = 0.0;
var emsq: Double = 0.0;
var gam: Double = 0.0;
var rtemsq: Double = 0.0;
var s1: Double = 0.0;
var s2: Double = 0.0;
var s3: Double = 0.0;
var s4: Double = 0.0;
var s5: Double = 0.0;
var s6: Double = 0.0;
var s7: Double = 0.0;
var ss1: Double = 0.0;
var ss2: Double = 0.0;
var ss3: Double = 0.0;
var ss4: Double = 0.0;
var ss5: Double = 0.0;
var ss6: Double = 0.0;
var ss7: Double = 0.0;
var sz1: Double = 0.0;
var sz2: Double = 0.0;
var sz3: Double = 0.0;
var sz11: Double = 0.0;
var sz12: Double = 0.0;
var sz13: Double = 0.0;
var sz21: Double = 0.0;
var sz22: Double = 0.0;
var sz23: Double = 0.0;
var sz31: Double = 0.0;
var sz32: Double = 0.0;
var sz33: Double = 0.0;
var nm: Double = 0.0;
var z1: Double = 0.0;
var z2: Double = 0.0;
var z3: Double = 0.0;
var z11: Double = 0.0;
var z12: Double = 0.0;
var z13: Double = 0.0;
var z21: Double = 0.0;
var z22: Double = 0.0;
var z23: Double = 0.0;
var z31: Double = 0.0;
var z32: Double = 0.0;
var z33: Double = 0.0;

        
        s1 = 0.0;
        s2 = 0.0;
        s3 = 0.0;
        s4 = 0.0;
        s5 = 0.0;
        s6 = 0.0;
        s7 = 0.0;
        ss1 = 0.0;
        ss2 = 0.0;
        ss3 = 0.0;
        ss4 = 0.0;
        ss5 = 0.0;
        ss6 = 0.0;
        ss7 = 0.0;
        sz1 = 0.0;
        sz2 = 0.0;
        sz3 = 0.0;
        sz11 = 0.0;
        sz12 = 0.0;
        sz13 = 0.0;
        sz21 = 0.0;
        sz22 = 0.0;
        sz23 = 0.0;
        sz31 = 0.0;
        sz32 = 0.0;
        sz33 = 0.0;
        z1 = 0.0;
        z2 = 0.0;
        z3 = 0.0;
        z11 = 0.0;
        z12 = 0.0;
        z13 = 0.0;
        z21 = 0.0;
        z22 = 0.0;
        z23 = 0.0;
        z31 = 0.0;
        z32 = 0.0;
        z33 = 0.0;

        
        var zes: Double = 0.01675;
        var zel: Double = 0.05490;
        var c1ss: Double = 2.9864797e-6;
        var c1l: Double = 4.7968065e-7;
        var zsinis: Double = 0.39785416;
        var zcosis: Double = 0.91744867;
        var zcosgs: Double = 0.1945905;
        var zsings: Double = -0.98088458;
        var twopi: Double = 2.0 * pi;

        
        var lsflg: Int = 0;
        var a1: Double = 0.0;
var a2: Double = 0.0;
var a3: Double = 0.0;
var a4: Double = 0.0;
var a5: Double = 0.0;
var a6: Double = 0.0;
var a7: Double = 0.0;
var a8: Double = 0.0;
var a9: Double = 0.0;
var a10: Double = 0.0;
var betasq: Double = 0.0;
var cc: Double = 0.0;
var ctem: Double = 0.0;
var stem: Double = 0.0;
var x1: Double = 0.0;
var x2: Double = 0.0;
var x3: Double = 0.0;
var x4: Double = 0.0;
var x5: Double = 0.0;
var x6: Double = 0.0;
var x7: Double = 0.0;
var x8: Double = 0.0;
var xnodce: Double = 0.0;
var xnoi: Double = 0.0;
var zcosg: Double = 0.0;
var zcosgl: Double = 0.0;
var zcosh: Double = 0.0;
var zcoshl: Double = 0.0;
var zcosi: Double = 0.0;
var zcosil: Double = 0.0;
var zsing: Double = 0.0;
var zsingl: Double = 0.0;
var zsinh: Double = 0.0;
var zsinhl: Double = 0.0;
var zsini: Double = 0.0;
var zsinil: Double = 0.0;
var zx: Double = 0.0;
var zy: Double = 0.0;

        nm = np;
        em = ep;
        snodm = Math.sin(nodep);
        cnodm = Math.cos(nodep);
        sinomm = Math.sin(argpp);
        cosomm = Math.cos(argpp);
        sinim = Math.sin(inclp);
        cosim = Math.cos(inclp);
        emsq = em * em;
        betasq = 1.0 - emsq;
        rtemsq = Math.sqrt(betasq);

        
        satrec.peo = 0.0;
        satrec.pinco = 0.0;
        satrec.plo = 0.0;
        satrec.pgho = 0.0;
        satrec.pho = 0.0;
        day = epoch + 18261.5 + tc / 1440.0;
        xnodce = ((4.5236020 - 9.2422029e-4 * day) % twopi);
        stem = Math.sin(xnodce);
        ctem = Math.cos(xnodce);
        zcosil = 0.91375164 - 0.03568096 * ctem;
        zsinil = Math.sqrt(1.0 - zcosil * zcosil);
        zsinhl = 0.089683511 * stem / zsinil;
        zcoshl = Math.sqrt(1.0 - zsinhl * zsinhl);
        gam = 5.8351514 + 0.0019443680 * day;
        zx = 0.39785416 * stem / zsinil;
        zy = zcoshl * ctem + 0.91744867 * zsinhl * stem;
        zx = Math.atan2(zx, zy);
        zx = gam + zx - xnodce;
        zcosgl = Math.cos(zx);
        zsingl = Math.sin(zx);

        
        zcosg = zcosgs;
        zsing = zsings;
        zcosi = zcosis;
        zsini = zsinis;
        zcosh = cnodm;
        zsinh = snodm;
        cc = c1ss;
        xnoi = 1.0 / nm;

        for(lsflg in 1..2)
        {
            a1 = zcosg * zcosh + zsing * zcosi * zsinh;
            a3 = -zsing * zcosh + zcosg * zcosi * zsinh;
            a7 = -zcosg * zsinh + zsing * zcosi * zcosh;
            a8 = zsing * zsini;
            a9 = zsing * zsinh + zcosg * zcosi * zcosh;
            a10 = zcosg * zsini;
            a2 = cosim * a7 + sinim * a8;
            a4 = cosim * a9 + sinim * a10;
            a5 = -sinim * a7 + cosim * a8;
            a6 = -sinim * a9 + cosim * a10;

            x1 = a1 * cosomm + a2 * sinomm;
            x2 = a3 * cosomm + a4 * sinomm;
            x3 = -a1 * sinomm + a2 * cosomm;
            x4 = -a3 * sinomm + a4 * cosomm;
            x5 = a5 * sinomm;
            x6 = a6 * sinomm;
            x7 = a5 * cosomm;
            x8 = a6 * cosomm;

            z31 = 12.0 * x1 * x1 - 3.0 * x3 * x3;
            z32 = 24.0 * x1 * x2 - 6.0 * x3 * x4;
            z33 = 12.0 * x2 * x2 - 3.0 * x4 * x4;
            z1 = 3.0 * (a1 * a1 + a2 * a2) + z31 * emsq;
            z2 = 6.0 * (a1 * a3 + a2 * a4) + z32 * emsq;
            z3 = 3.0 * (a3 * a3 + a4 * a4) + z33 * emsq;
            z11 = -6.0 * a1 * a5 + emsq * (-24.0 * x1 * x7 - 6.0 * x3 * x5);
            z12 = -6.0 * (a1 * a6 + a3 * a5) + emsq *
                    (-24.0 * (x2 * x7 + x1 * x8) - 6.0 * (x3 * x6 + x4 * x5));
            z13 = -6.0 * a3 * a6 + emsq * (-24.0 * x2 * x8 - 6.0 * x4 * x6);
            z21 = 6.0 * a2 * a5 + emsq * (24.0 * x1 * x5 - 6.0 * x3 * x7);
            z22 = 6.0 * (a4 * a5 + a2 * a6) + emsq *
                    (24.0 * (x2 * x5 + x1 * x6) - 6.0 * (x4 * x7 + x3 * x8));
            z23 = 6.0 * a4 * a6 + emsq * (24.0 * x2 * x6 - 6.0 * x4 * x8);
            z1 = z1 + z1 + betasq * z31;
            z2 = z2 + z2 + betasq * z32;
            z3 = z3 + z3 + betasq * z33;
            s3 = cc * xnoi;
            s2 = -0.5 * s3 / rtemsq;
            s4 = s3 * rtemsq;
            s1 = -15.0 * em * s4;
            s5 = x1 * x3 + x2 * x4;
            s6 = x2 * x3 + x1 * x4;
            s7 = x2 * x4 - x1 * x3;

            
            if(lsflg == 1)
            {
                ss1 = s1;
                ss2 = s2;
                ss3 = s3;
                ss4 = s4;
                ss5 = s5;
                ss6 = s6;
                ss7 = s7;
                sz1 = z1;
                sz2 = z2;
                sz3 = z3;
                sz11 = z11;
                sz12 = z12;
                sz13 = z13;
                sz21 = z21;
                sz22 = z22;
                sz23 = z23;
                sz31 = z31;
                sz32 = z32;
                sz33 = z33;
                zcosg = zcosgl;
                zsing = zsingl;
                zcosi = zcosil;
                zsini = zsinil;
                zcosh = zcoshl * cnodm + zsinhl * snodm;
                zsinh = snodm * zcoshl - cnodm * zsinhl;
                cc = c1l;
            }
        }

        satrec.zmol = ((4.7199672 + 0.22997150 * day - gam) % twopi);
        satrec.zmos = ((6.2565837 + 0.017201977 * day) % twopi);

        
        satrec.se2 = 2.0 * ss1 * ss6;
        satrec.se3 = 2.0 * ss1 * ss7;
        satrec.si2 = 2.0 * ss2 * sz12;
        satrec.si3 = 2.0 * ss2 * (sz13 - sz11);
        satrec.sl2 = -2.0 * ss3 * sz2;
        satrec.sl3 = -2.0 * ss3 * (sz3 - sz1);
        satrec.sl4 = -2.0 * ss3 * (-21.0 - 9.0 * emsq) * zes;
        satrec.sgh2 = 2.0 * ss4 * sz32;
        satrec.sgh3 = 2.0 * ss4 * (sz33 - sz31);
        satrec.sgh4 = -18.0 * ss4 * zes;
        satrec.sh2 = -2.0 * ss2 * sz22;
        satrec.sh3 = -2.0 * ss2 * (sz23 - sz21);

        
        satrec.ee2 = 2.0 * s1 * s6;
        satrec.e3 = 2.0 * s1 * s7;
        satrec.xi2 = 2.0 * s2 * z12;
        satrec.xi3 = 2.0 * s2 * (z13 - z11);
        satrec.xl2 = -2.0 * s3 * z2;
        satrec.xl3 = -2.0 * s3 * (z3 - z1);
        satrec.xl4 = -2.0 * s3 * (-21.0 - 9.0 * emsq) * zel;
        satrec.xgh2 = 2.0 * s4 * z32;
        satrec.xgh3 = 2.0 * s4 * (z33 - z31);
        satrec.xgh4 = -18.0 * s4 * zel;
        satrec.xh2 = -2.0 * s2 * z22;
        satrec.xh3 = -2.0 * s2 * (z23 - z21);

        return doubleArrayOf(
                    snodm, cnodm, sinim, cosim, sinomm, cosomm, day, em, emsq, gam, rtemsq, s1, s2, s3,
                    s4, s5, s6, s7, ss1, ss2, ss3, ss4, ss5, ss6, ss7, sz1, sz2, sz3, sz11, sz12, sz13,
                    sz21, sz22, sz23, sz31, sz32, sz33, nm, z1, z2, z3, z11, z12, z13, z21, z22, z23, z31, z32, z33
                );


    
    }
    private fun dsinit(input_whichconst: Gravconsttype, input_cosim: Double, input_emsq: Double, input_argpo: Double, input_s1: Double, input_s2: Double, input_s3: Double, input_s4: Double, input_s5: Double, input_sinim: Double, input_ss1: Double, input_ss2: Double, input_ss3: Double, input_ss4: Double, input_ss5: Double, input_sz1: Double, input_sz3: Double, input_sz11: Double, input_sz13: Double, input_sz21: Double, input_sz23: Double, input_sz31: Double, input_sz33: Double, input_t: Double, input_tc: Double, input_gsto: Double, input_mo: Double, input_mdot: Double, input_no: Double, input_nodeo: Double, input_nodedot: Double, input_xpidot: Double, input_z1: Double, input_z3: Double, input_z11: Double, input_z13: Double, input_z21: Double, input_z23: Double, input_z31: Double, input_z33: Double, input_ecco: Double, input_eccsq: Double, input_satrec: Sgp4Record, input_em: Double, input_argpm: Double, input_inclm: Double, input_mm: Double, input_nm: Double, input_nodem: Double): DoubleArray {
        var whichconst = input_whichconst
        var cosim = input_cosim
        var emsq = input_emsq
        var argpo = input_argpo
        var s1 = input_s1
        var s2 = input_s2
        var s3 = input_s3
        var s4 = input_s4
        var s5 = input_s5
        var sinim = input_sinim
        var ss1 = input_ss1
        var ss2 = input_ss2
        var ss3 = input_ss3
        var ss4 = input_ss4
        var ss5 = input_ss5
        var sz1 = input_sz1
        var sz3 = input_sz3
        var sz11 = input_sz11
        var sz13 = input_sz13
        var sz21 = input_sz21
        var sz23 = input_sz23
        var sz31 = input_sz31
        var sz33 = input_sz33
        var t = input_t
        var tc = input_tc
        var gsto = input_gsto
        var mo = input_mo
        var mdot = input_mdot
        var no = input_no
        var nodeo = input_nodeo
        var nodedot = input_nodedot
        var xpidot = input_xpidot
        var z1 = input_z1
        var z3 = input_z3
        var z11 = input_z11
        var z13 = input_z13
        var z21 = input_z21
        var z23 = input_z23
        var z31 = input_z31
        var z33 = input_z33
        var ecco = input_ecco
        var eccsq = input_eccsq
        var satrec = input_satrec
        var em = input_em
        var argpm = input_argpm
        var inclm = input_inclm
        var mm = input_mm
        var nm = input_nm
        var nodem = input_nodem

        
        var dndt: Double = 0.0; 

        
        var twopi: Double = 2.0 * pi;

        var ainv2: Double = 0.0;
var aonv: Double = 0.0;
var cosisq: Double = 0.0;
var eoc: Double = 0.0;
var f220: Double = 0.0;
var f221: Double = 0.0;
var f311: Double = 0.0;
var f321: Double = 0.0;
var f322: Double = 0.0;
var f330: Double = 0.0;
var f441: Double = 0.0;
var f442: Double = 0.0;
var f522: Double = 0.0;
var f523: Double = 0.0;
var f542: Double = 0.0;
var f543: Double = 0.0;
var g200: Double = 0.0;
var g201: Double = 0.0;
var g211: Double = 0.0;
var g300: Double = 0.0;
var g310: Double = 0.0;
var g322: Double = 0.0;
var g410: Double = 0.0;
var g422: Double = 0.0;
var g520: Double = 0.0;
var g521: Double = 0.0;
var g532: Double = 0.0;
var g533: Double = 0.0;
var ses: Double = 0.0;
var sgs: Double = 0.0;
var sghl: Double = 0.0;
var sghs: Double = 0.0;
var shs: Double = 0.0;
var shll: Double = 0.0;
var sis: Double = 0.0;
var sini2: Double = 0.0;
var sls: Double = 0.0;
var temp: Double = 0.0;
var temp1: Double = 0.0;
var theta: Double = 0.0;
var xno2: Double = 0.0;
var q22: Double = 0.0;
var q31: Double = 0.0;
var q33: Double = 0.0;
var root22: Double = 0.0;
var root44: Double = 0.0;
var root54: Double = 0.0;
var rptim: Double = 0.0;
var root32: Double = 0.0;
var root52: Double = 0.0;
var x2o3: Double = 0.0;
var xke: Double = 0.0;
var znl: Double = 0.0;
var emo: Double = 0.0;
var zns: Double = 0.0;
var emsqo: Double = 0.0;
var tumin: Double = 0.0;
var mu: Double = 0.0;
var radiusearthkm: Double = 0.0;
var j2: Double = 0.0;
var j3: Double = 0.0;
var j4: Double = 0.0;
var j3oj2: Double = 0.0;

        q22 = 1.7891679e-6;
        q31 = 2.1460748e-6;
        q33 = 2.2123015e-7;
        root22 = 1.7891679e-6;
        root44 = 7.3636953e-9;
        root54 = 2.1765803e-9;
        rptim = 4.37526908801129966e-3; 
        root32 = 3.7393792e-7;
        root52 = 1.1428639e-7;
        x2o3 = 2.0 / 3.0;
        znl = 1.5835218e-4;
        zns = 1.19459e-5;

        
        var temp2: DoubleArray = getgravconst(whichconst);
        tumin = temp2[0];
        mu = temp2[1];
        radiusearthkm = temp2[2];
        xke = temp2[3];
        j2 = temp2[4];
        j3 = temp2[5];
        j4 = temp2[6];
        j3oj2 = temp2[7];

        
        satrec.irez = 0;
        if((nm < 0.0052359877) && (nm > 0.0034906585))
        {
            satrec.irez = 1;
        }
        if((nm >= 8.26e-3) && (nm <= 9.24e-3) && (em >= 0.5))
        {
            satrec.irez = 2;
        }

        
        ses = ss1 * zns * ss5;
        sis = ss2 * zns * (sz11 + sz13);
        sls = -zns * ss3 * (sz1 + sz3 - 14.0 - 6.0 * emsq);
        sghs = ss4 * zns * (sz31 + sz33 - 6.0);
        shs = -zns * ss2 * (sz21 + sz23);
        
        if((inclm < 5.2359877e-2) || (inclm > pi - 5.2359877e-2))
        {
            shs = 0.0;
        }
        if(sinim != 0.0)
        {
            shs = shs / sinim;
        }
        sgs = sghs - cosim * shs;

        
        satrec.dedt = ses + s1 * znl * s5;
        satrec.didt = sis + s2 * znl * (z11 + z13);
        satrec.dmdt = sls - znl * s3 * (z1 + z3 - 14.0 - 6.0 * emsq);
        sghl = s4 * znl * (z31 + z33 - 6.0);
        shll = -znl * s2 * (z21 + z23);
        
        if((inclm < 5.2359877e-2) || (inclm > pi - 5.2359877e-2))
        {
            shll = 0.0;
        }
        satrec.domdt = sgs + sghl;
        satrec.dnodt = shs;
        if(sinim != 0.0)
        {
            satrec.domdt = satrec.domdt - cosim / sinim * shll;
            satrec.dnodt = satrec.dnodt + shll / sinim;
        }

        
        dndt = 0.0;
        theta = ((gsto + tc * rptim) % twopi);
        em = em + satrec.dedt * t;
        inclm = inclm + satrec.didt * t;
        argpm = argpm + satrec.domdt * t;
        nodem = nodem + satrec.dnodt * t;
        mm = mm + satrec.dmdt * t;
        
        
        
        
        
        
        
        

        
        if(satrec.irez != 0)
        {
            aonv = Math.pow(nm / xke, x2o3);

            
            if(satrec.irez == 2)
            {
                cosisq = cosim * cosim;
                emo = em;
                em = ecco;
                emsqo = emsq;
                emsq = eccsq;
                eoc = em * emsq;
                g201 = -0.306 - (em - 0.64) * 0.440;

                if(em <= 0.65)
                {
                    g211 = 3.616 - 13.2470 * em + 16.2900 * emsq;
                    g310 = -19.302 + 117.3900 * em - 228.4190 * emsq + 156.5910 * eoc;
                    g322 = -18.9068 + 109.7927 * em - 214.6334 * emsq + 146.5816 * eoc;
                    g410 = -41.122 + 242.6940 * em - 471.0940 * emsq + 313.9530 * eoc;
                    g422 = -146.407 + 841.8800 * em - 1629.014 * emsq + 1083.4350 * eoc;
                    g520 = -532.114 + 3017.977 * em - 5740.032 * emsq + 3708.2760 * eoc;
                }
                else
                {
                    g211 = -72.099 + 331.819 * em - 508.738 * emsq + 266.724 * eoc;
                    g310 = -346.844 + 1582.851 * em - 2415.925 * emsq + 1246.113 * eoc;
                    g322 = -342.585 + 1554.908 * em - 2366.899 * emsq + 1215.972 * eoc;
                    g410 = -1052.797 + 4758.686 * em - 7193.992 * emsq + 3651.957 * eoc;
                    g422 = -3581.690 + 16178.110 * em - 24462.770 * emsq + 12422.520 * eoc;
                    if(em > 0.715)
                    {
                        g520 = -5149.66 + 29936.92 * em - 54087.36 * emsq + 31324.56 * eoc;
                    }
                    else
                    {
                        g520 = 1464.74 - 4664.75 * em + 3763.64 * emsq;
                    }
                }
                if(em < 0.7)
                {
                    g533 = -919.22770 + 4988.6100 * em - 9064.7700 * emsq + 5542.21 * eoc;
                    g521 = -822.71072 + 4568.6173 * em - 8491.4146 * emsq + 5337.524 * eoc;
                    g532 = -853.66600 + 4690.2500 * em - 8624.7700 * emsq + 5341.4 * eoc;
                }
                else
                {
                    g533 = -37995.780 + 161616.52 * em - 229838.20 * emsq + 109377.94 * eoc;
                    g521 = -51752.104 + 218913.95 * em - 309468.16 * emsq + 146349.42 * eoc;
                    g532 = -40023.880 + 170470.89 * em - 242699.48 * emsq + 115605.82 * eoc;
                }

                sini2 = sinim * sinim;
                f220 = 0.75 * (1.0 + 2.0 * cosim + cosisq);
                f221 = 1.5 * sini2;
                f321 = 1.875 * sinim * (1.0 - 2.0 * cosim - 3.0 * cosisq);
                f322 = -1.875 * sinim * (1.0 + 2.0 * cosim - 3.0 * cosisq);
                f441 = 35.0 * sini2 * f220;
                f442 = 39.3750 * sini2 * sini2;
                f522 = 9.84375 * sinim * (sini2 * (1.0 - 2.0 * cosim - 5.0 * cosisq) +
                        0.33333333 * (-2.0 + 4.0 * cosim + 6.0 * cosisq));
                f523 = sinim * (4.92187512 * sini2 * (-2.0 - 4.0 * cosim +
                        10.0 * cosisq) + 6.56250012 * (1.0 + 2.0 * cosim - 3.0 * cosisq));
                f542 = 29.53125 * sinim * (2.0 - 8.0 * cosim + cosisq *
                        (-12.0 + 8.0 * cosim + 10.0 * cosisq));
                f543 = 29.53125 * sinim * (-2.0 - 8.0 * cosim + cosisq *
                        (12.0 + 8.0 * cosim - 10.0 * cosisq));
                xno2 = nm * nm;
                ainv2 = aonv * aonv;
                temp1 = 3.0 * xno2 * ainv2;
                temp = temp1 * root22;
                satrec.d2201 = temp * f220 * g201;
                satrec.d2211 = temp * f221 * g211;
                temp1 = temp1 * aonv;
                temp = temp1 * root32;
                satrec.d3210 = temp * f321 * g310;
                satrec.d3222 = temp * f322 * g322;
                temp1 = temp1 * aonv;
                temp = 2.0 * temp1 * root44;
                satrec.d4410 = temp * f441 * g410;
                satrec.d4422 = temp * f442 * g422;
                temp1 = temp1 * aonv;
                temp = temp1 * root52;
                satrec.d5220 = temp * f522 * g520;
                satrec.d5232 = temp * f523 * g532;
                temp = 2.0 * temp1 * root54;
                satrec.d5421 = temp * f542 * g521;
                satrec.d5433 = temp * f543 * g533;
                satrec.xlamo = ((mo + nodeo + nodeo - theta - theta) % twopi);
                satrec.xfact = mdot + satrec.dmdt + 2.0 * (nodedot + satrec.dnodt - rptim) - no;
                em = emo;
                emsq = emsqo;
            }

            
            if(satrec.irez == 1)
            {
                g200 = 1.0 + emsq * (-2.5 + 0.8125 * emsq);
                g310 = 1.0 + 2.0 * emsq;
                g300 = 1.0 + emsq * (-6.0 + 6.60937 * emsq);
                f220 = 0.75 * (1.0 + cosim) * (1.0 + cosim);
                f311 = 0.9375 * sinim * sinim * (1.0 + 3.0 * cosim) - 0.75 * (1.0 + cosim);
                f330 = 1.0 + cosim;
                f330 = 1.875 * f330 * f330 * f330;
                satrec.del1 = 3.0 * nm * nm * aonv * aonv;
                satrec.del2 = 2.0 * satrec.del1 * f220 * g200 * q22;
                satrec.del3 = 3.0 * satrec.del1 * f330 * g300 * q33 * aonv;
                satrec.del1 = satrec.del1 * f311 * g310 * q31 * aonv;
                satrec.xlamo = ((mo + nodeo + argpo - theta) % twopi);
                satrec.xfact = mdot + xpidot - rptim + satrec.dmdt + satrec.domdt + satrec.dnodt - no;
            }

            
            satrec.xli = satrec.xlamo;
            satrec.xni = no;
            satrec.atime = 0.0;
            nm = no + dndt;
        }

        return doubleArrayOf(
                    em, argpm, inclm, mm, nm, nodem, dndt
                );


    
    }
    private fun dspace(input_irez: Int, input_d2201: Double, input_d2211: Double, input_d3210: Double, input_d3222: Double, input_d4410: Double, input_d4422: Double, input_d5220: Double, input_d5232: Double, input_d5421: Double, input_d5433: Double, input_dedt: Double, input_del1: Double, input_del2: Double, input_del3: Double, input_didt: Double, input_dmdt: Double, input_dnodt: Double, input_domdt: Double, input_argpo: Double, input_argpdot: Double, input_t: Double, input_tc: Double, input_gsto: Double, input_xfact: Double, input_xlamo: Double, input_no: Double, input_em: Double, input_argpm: Double, input_inclm: Double, input_mm: Double, input_nodem: Double, input_satrec: Sgp4Record, input_nm: Double): DoubleArray {
        var irez = input_irez
        var d2201 = input_d2201
        var d2211 = input_d2211
        var d3210 = input_d3210
        var d3222 = input_d3222
        var d4410 = input_d4410
        var d4422 = input_d4422
        var d5220 = input_d5220
        var d5232 = input_d5232
        var d5421 = input_d5421
        var d5433 = input_d5433
        var dedt = input_dedt
        var del1 = input_del1
        var del2 = input_del2
        var del3 = input_del3
        var didt = input_didt
        var dmdt = input_dmdt
        var dnodt = input_dnodt
        var domdt = input_domdt
        var argpo = input_argpo
        var argpdot = input_argpdot
        var t = input_t
        var tc = input_tc
        var gsto = input_gsto
        var xfact = input_xfact
        var xlamo = input_xlamo
        var no = input_no
        var em = input_em
        var argpm = input_argpm
        var inclm = input_inclm
        var mm = input_mm
        var nodem = input_nodem
        var satrec = input_satrec
        var nm = input_nm

        
        

        
        var dndt: Double = 0.0;

        var twopi: Double = 2.0 * pi;
        var iretn: Int = 0;
var iret: Int = 0;
        var delt: Double = 0.0;
var ft: Double = 0.0;
var theta: Double = 0.0;
var x2li: Double = 0.0;
var x2omi: Double = 0.0;
var xl: Double = 0.0;
var xldot: Double = 0.0;
var xnddt: Double = 0.0;
var xndt: Double = 0.0;
var xomi: Double = 0.0;
var g22: Double = 0.0;
var g32: Double = 0.0;
var g44: Double = 0.0;
var g52: Double = 0.0;
var g54: Double = 0.0;
var fasx2: Double = 0.0;
var fasx4: Double = 0.0;
var fasx6: Double = 0.0;
var rptim: Double = 0.0;
var step2: Double = 0.0;
var stepn: Double = 0.0;
var stepp: Double = 0.0;

        
        xndt = 0.0;
        xnddt = 0.0;
        xldot = 0.0;

        fasx2 = 0.13130908;
        fasx4 = 2.8843198;
        fasx6 = 0.37448087;
        g22 = 5.7686396;
        g32 = 0.95240898;
        g44 = 1.8014998;
        g52 = 1.0508330;
        g54 = 4.4108898;
        rptim = 4.37526908801129966e-3; 
        stepp = 720.0;
        stepn = -720.0;
        step2 = 259200.0;

        
        dndt = 0.0;
        theta = ((gsto + tc * rptim) % twopi);
        em = em + dedt * t;

        inclm = inclm + didt * t;
        argpm = argpm + domdt * t;
        nodem = nodem + dnodt * t;
        mm = mm + dmdt * t;

        
        
        
        
        
        
        
        

        
        
        
        
        

        
        ft = 0.0;
        if(irez != 0)
        {
            
            if((satrec.atime == 0.0) || (t * satrec.atime <= 0.0) || (Math.abs(t) < Math.abs(satrec.atime)))
            {
                satrec.atime = 0.0;
                satrec.xni = no;
                satrec.xli = xlamo;
            }
            
            if(t > 0.0)
            {
                delt = stepp;
            }
            else
            {
                delt = stepn;
            }

            iretn = 381; 
            iret = 0; 
            while(iretn == 381)
            {
                
                
                if(irez != 2)
                {
                    xndt = del1 * Math.sin(satrec.xli - fasx2) + del2 * Math.sin(2.0 * (satrec.xli - fasx4)) +
                            del3 * Math.sin(3.0 * (satrec.xli - fasx6));
                    xldot = satrec.xni + xfact;
                    xnddt = del1 * Math.cos(satrec.xli - fasx2) +
                            2.0 * del2 * Math.cos(2.0 * (satrec.xli - fasx4)) +
                            3.0 * del3 * Math.cos(3.0 * (satrec.xli - fasx6));
                    xnddt = xnddt * xldot;
                }
                else
                {
                    
                    xomi = argpo + argpdot * satrec.atime;
                    x2omi = xomi + xomi;
                    x2li = satrec.xli + satrec.xli;
                    xndt = d2201 * Math.sin(x2omi + satrec.xli - g22) + d2211 * Math.sin(satrec.xli - g22) +
                            d3210 * Math.sin(xomi + satrec.xli - g32) + d3222 * Math.sin(-xomi + satrec.xli - g32) +
                            d4410 * Math.sin(x2omi + x2li - g44) + d4422 * Math.sin(x2li - g44) +
                            d5220 * Math.sin(xomi + satrec.xli - g52) + d5232 * Math.sin(-xomi + satrec.xli - g52) +
                            d5421 * Math.sin(xomi + x2li - g54) + d5433 * Math.sin(-xomi + x2li - g54);
                    xldot = satrec.xni + xfact;
                    xnddt = d2201 * Math.cos(x2omi + satrec.xli - g22) + d2211 * Math.cos(satrec.xli - g22) +
                            d3210 * Math.cos(xomi + satrec.xli - g32) + d3222 * Math.cos(-xomi + satrec.xli - g32) +
                            d5220 * Math.cos(xomi + satrec.xli - g52) + d5232 * Math.cos(-xomi + satrec.xli - g52) +
                            2.0 * (d4410 * Math.cos(x2omi + x2li - g44) +
                            d4422 * Math.cos(x2li - g44) + d5421 * Math.cos(xomi + x2li - g54) +
                            d5433 * Math.cos(-xomi + x2li - g54));
                    xnddt = xnddt * xldot;
                }

                
                
                if(Math.abs(t - satrec.atime) >= stepp)
                {
                    iret = 0;
                    iretn = 381;
                }
                else 
                {
                    ft = t - satrec.atime;
                    iretn = 0;
                }

                if(iretn == 381)
                {
                    satrec.xli = satrec.xli + xldot * delt + xndt * step2;
                    satrec.xni = satrec.xni + xndt * delt + xnddt * step2;
                    satrec.atime = satrec.atime + delt;
                }
            }  

            nm = satrec.xni + xndt * ft + xnddt * ft * ft * 0.5;
            xl = satrec.xli + xldot * ft + xndt * ft * ft * 0.5;
            if(irez != 1)
            {
                mm = xl - 2.0 * nodem + 2.0 * theta;
                dndt = nm - no;
            }
            else
            {
                mm = xl - nodem - argpm + theta;
                dndt = nm - no;
            }
            nm = no + dndt;
        }

        return doubleArrayOf(
                    em, argpm, inclm, mm, nodem, dndt, nm
                );


    
    }
    fun initl(input_satn: Int, input_whichconst: Gravconsttype, input_ecco: Double, input_epoch: Double, input_inclo: Double, input_satrec: Sgp4Record): DoubleArray {
        var satn = input_satn
        var whichconst = input_whichconst
        var ecco = input_ecco
        var epoch = input_epoch
        var inclo = input_inclo
        var satrec = input_satrec

        
        
        var ainv: Double = 0.0;
var ao: Double = 0.0;
var con42: Double = 0.0;
var cosio: Double = 0.0;
var cosio2: Double = 0.0;
var eccsq: Double = 0.0;
var omeosq: Double = 0.0;
var posq: Double = 0.0;
var rp: Double = 0.0;
var rteosq: Double = 0.0;
var sinio: Double = 0.0;

        
        var ak: Double = 0.0;
var d1: Double = 0.0;
var del: Double = 0.0;
var adel: Double = 0.0;
var po: Double = 0.0;
var x2o3: Double = 0.0;
var j2: Double = 0.0;
var xke: Double = 0.0;
var tumin: Double = 0.0;
var mu: Double = 0.0;
var radiusearthkm: Double = 0.0;
var j3: Double = 0.0;
var j4: Double = 0.0;
var j3oj2: Double = 0.0;

        
        var ds70: Double = 0.0;
        var ts70: Double = 0.0;
var tfrac: Double = 0.0;
var c1: Double = 0.0;
var thgr70: Double = 0.0;
var fk5r: Double = 0.0;
var c1p2p: Double = 0.0;
var thgr: Double = 0.0;
var thgro: Double = 0.0;
        var twopi: Double = 2.0 * pi;

        
        
        var temp2: DoubleArray = getgravconst(whichconst);
        tumin = temp2[0];
        mu = temp2[1];
        radiusearthkm = temp2[2];
        xke = temp2[3];
        j2 = temp2[4];
        j3 = temp2[5];
        j4 = temp2[6];
        j3oj2 = temp2[7];

        x2o3 = 2.0 / 3.0;

        
        eccsq = ecco * ecco;
        omeosq = 1.0 - eccsq;
        rteosq = Math.sqrt(omeosq);
        cosio = Math.cos(inclo);
        cosio2 = cosio * cosio;

        
        ak = Math.pow(xke / satrec.no, x2o3);
        d1 = 0.75 * j2 * (3.0 * cosio2 - 1.0) / (rteosq * omeosq);
        del = d1 / (ak * ak);
        adel = ak * (1.0 - del * del - del *
                (1.0 / 3.0 + 134.0 * del * del / 81.0));
        del = d1 / (adel * adel);
        satrec.no = satrec.no / (1.0 + del);

        ao = Math.pow(xke / satrec.no, x2o3);
        sinio = Math.sin(inclo);
        po = ao * omeosq;
        con42 = 1.0 - 5.0 * cosio2;
        satrec.con41 = -con42 - cosio2 - cosio2;
        ainv = 1.0 / ao;
        posq = po * po;
        rp = ao * (1.0 - ecco);
        satrec.method = 'n';

        
        if(satrec.operationmode == 'a')
        {
            
            
            ts70 = epoch - 7305.0;
            ds70 = Math.floor(ts70 + 1.0e-8);
            tfrac = ts70 - ds70;
            
            c1 = 1.72027916940703639e-2;
            thgr70 = 1.7321343856509374;
            fk5r = 5.07551419432269442e-15;
            c1p2p = c1 + twopi;
            satrec.gsto = ((thgr70 + c1 * ds70 + c1p2p * tfrac + ts70 * ts70 * fk5r) % twopi);
            if(satrec.gsto < 0.0)
            {
                satrec.gsto = satrec.gsto + twopi;
            }
        }
        else
        {
            satrec.gsto = gstime(epoch + 2433281.5);
        }

        return doubleArrayOf(
                    ainv, ao, con42, cosio, cosio2, eccsq, omeosq, posq, rp, rteosq, sinio
                );


    
    }
    fun sgp4init(input_whichconst: Gravconsttype, input_opsmode: Char, input_satn: Int, input_epoch: Double, input_xbstar: Double, input_xecco: Double, input_xargpo: Double, input_xinclo: Double, input_xmo: Double, input_xno: Double, input_xnodeo: Double, input_satrec: Sgp4Record): Boolean {
        var whichconst = input_whichconst
        var opsmode = input_opsmode
        var satn = input_satn
        var epoch = input_epoch
        var xbstar = input_xbstar
        var xecco = input_xecco
        var xargpo = input_xargpo
        var xinclo = input_xinclo
        var xmo = input_xmo
        var xno = input_xno
        var xnodeo = input_xnodeo
        var satrec = input_satrec

        
        var ao: Double = 0.0;
var ainv: Double = 0.0;
var con42: Double = 0.0;
var cosio: Double = 0.0;
var sinio: Double = 0.0;
var cosio2: Double = 0.0;
var eccsq: Double = 0.0;
var omeosq: Double = 0.0;
var posq: Double = 0.0;
var rp: Double = 0.0;
var rteosq: Double = 0.0;
var cnodm: Double = 0.0;
var snodm: Double = 0.0;
var cosim: Double = 0.0;
var sinim: Double = 0.0;
var cosomm: Double = 0.0;
var sinomm: Double = 0.0;
var cc1sq: Double = 0.0;
var cc2: Double = 0.0;
var cc3: Double = 0.0;
var coef: Double = 0.0;
var coef1: Double = 0.0;
var cosio4: Double = 0.0;
var day: Double = 0.0;
var dndt: Double = 0.0;
var em: Double = 0.0;
var emsq: Double = 0.0;
var eeta: Double = 0.0;
var etasq: Double = 0.0;
var gam: Double = 0.0;
var argpm: Double = 0.0;
var nodem: Double = 0.0;
var inclm: Double = 0.0;
var mm: Double = 0.0;
var nm: Double = 0.0;
var perige: Double = 0.0;
var pinvsq: Double = 0.0;
var psisq: Double = 0.0;
var qzms24: Double = 0.0;
var rtemsq: Double = 0.0;
var s1: Double = 0.0;
var s2: Double = 0.0;
var s3: Double = 0.0;
var s4: Double = 0.0;
var s5: Double = 0.0;
var s6: Double = 0.0;
var s7: Double = 0.0;
var sfour: Double = 0.0;
var ss1: Double = 0.0;
var ss2: Double = 0.0;
var ss3: Double = 0.0;
var ss4: Double = 0.0;
var ss5: Double = 0.0;
var ss6: Double = 0.0;
var ss7: Double = 0.0;
var sz1: Double = 0.0;
var sz2: Double = 0.0;
var sz3: Double = 0.0;
var sz11: Double = 0.0;
var sz12: Double = 0.0;
var sz13: Double = 0.0;
var sz21: Double = 0.0;
var sz22: Double = 0.0;
var sz23: Double = 0.0;
var sz31: Double = 0.0;
var sz32: Double = 0.0;
var sz33: Double = 0.0;
var tc: Double = 0.0;
var temp: Double = 0.0;
var temp1: Double = 0.0;
var temp2: Double = 0.0;
var temp3: Double = 0.0;
var tsi: Double = 0.0;
var xpidot: Double = 0.0;
var xhdot1: Double = 0.0;
var z1: Double = 0.0;
var z2: Double = 0.0;
var z3: Double = 0.0;
var z11: Double = 0.0;
var z12: Double = 0.0;
var z13: Double = 0.0;
var z21: Double = 0.0;
var z22: Double = 0.0;
var z23: Double = 0.0;
var z31: Double = 0.0;
var z32: Double = 0.0;
var z33: Double = 0.0;
var qzms2t: Double = 0.0;
var ss: Double = 0.0;
var j2: Double = 0.0;
var j3oj2: Double = 0.0;
var j4: Double = 0.0;
var x2o3: Double = 0.0;
var tumin: Double = 0.0;
var mu: Double = 0.0;
var radiusearthkm: Double = 0.0;
var xke: Double = 0.0;
var j3: Double = 0.0;
        var r: DoubleArray = DoubleArray(3);
        var v: DoubleArray = DoubleArray(3);

        
        
        
        
        var temp4: Double = 1.5e-12;

        
        satrec.isimp = 0;
        satrec.method = 'n';
        satrec.aycof = 0.0;
        satrec.con41 = 0.0;
        satrec.cc1 = 0.0;
        satrec.cc4 = 0.0;
        satrec.cc5 = 0.0;
        satrec.d2 = 0.0;
        satrec.d3 = 0.0;
        satrec.d4 = 0.0;
        satrec.delmo = 0.0;
        satrec.eta = 0.0;
        satrec.argpdot = 0.0;
        satrec.omgcof = 0.0;
        satrec.sinmao = 0.0;
        satrec.t = 0.0;
        satrec.t2cof = 0.0;
        satrec.t3cof = 0.0;
        satrec.t4cof = 0.0;
        satrec.t5cof = 0.0;
        satrec.x1mth2 = 0.0;
        satrec.x7thm1 = 0.0;
        satrec.mdot = 0.0;
        satrec.nodedot = 0.0;
        satrec.xlcof = 0.0;
        satrec.xmcof = 0.0;
        satrec.nodecf = 0.0;

        
        satrec.irez = 0;
        satrec.d2201 = 0.0;
        satrec.d2211 = 0.0;
        satrec.d3210 = 0.0;
        satrec.d3222 = 0.0;
        satrec.d4410 = 0.0;
        satrec.d4422 = 0.0;
        satrec.d5220 = 0.0;
        satrec.d5232 = 0.0;
        satrec.d5421 = 0.0;
        satrec.d5433 = 0.0;
        satrec.dedt = 0.0;
        satrec.del1 = 0.0;
        satrec.del2 = 0.0;
        satrec.del3 = 0.0;
        satrec.didt = 0.0;
        satrec.dmdt = 0.0;
        satrec.dnodt = 0.0;
        satrec.domdt = 0.0;
        satrec.e3 = 0.0;
        satrec.ee2 = 0.0;
        satrec.peo = 0.0;
        satrec.pgho = 0.0;
        satrec.pho = 0.0;
        satrec.pinco = 0.0;
        satrec.plo = 0.0;
        satrec.se2 = 0.0;
        satrec.se3 = 0.0;
        satrec.sgh2 = 0.0;
        satrec.sgh3 = 0.0;
        satrec.sgh4 = 0.0;
        satrec.sh2 = 0.0;
        satrec.sh3 = 0.0;
        satrec.si2 = 0.0;
        satrec.si3 = 0.0;
        satrec.sl2 = 0.0;
        satrec.sl3 = 0.0;
        satrec.sl4 = 0.0;
        satrec.gsto = 0.0;
        satrec.xfact = 0.0;
        satrec.xgh2 = 0.0;
        satrec.xgh3 = 0.0;
        satrec.xgh4 = 0.0;
        satrec.xh2 = 0.0;
        satrec.xh3 = 0.0;
        satrec.xi2 = 0.0;
        satrec.xi3 = 0.0;
        satrec.xl2 = 0.0;
        satrec.xl3 = 0.0;
        satrec.xl4 = 0.0;
        satrec.xlamo = 0.0;
        satrec.zmol = 0.0;
        satrec.zmos = 0.0;
        satrec.atime = 0.0;
        satrec.xli = 0.0;
        satrec.xni = 0.0;

        
        
        
        
        satrec.bstar = xbstar;
        satrec.ecco = xecco;
        satrec.argpo = xargpo;
        satrec.inclo = xinclo;
        satrec.mo = xmo;
        satrec.no = xno;
        satrec.nodeo = xnodeo;

        
        satrec.operationmode = opsmode;

        
        satrec.gravconsttype = whichconst;

        
        
        var temp5: DoubleArray = getgravconst(whichconst);
        tumin = temp5[0];
        mu = temp5[1];
        radiusearthkm = temp5[2];
        xke = temp5[3];
        j2 = temp5[4];
        j3 = temp5[5];
        j4 = temp5[6];
        j3oj2 = temp5[7];

        ss = 78.0 / radiusearthkm + 1.0;
        qzms2t = Math.pow(((120.0 - 78.0) / radiusearthkm), 4.0);
        x2o3 = 2.0 / 3.0;

        satrec.init = 'y';
        satrec.t = 0.0;

        var ttemp: DoubleArray = initl(satn, whichconst, satrec.ecco, epoch, satrec.inclo, satrec);
        ainv = ttemp[0];
        ao = ttemp[1];
        con42 = ttemp[2];
        cosio = ttemp[3];
        cosio2 = ttemp[4];
        eccsq = ttemp[5];
        omeosq = ttemp[6];
        posq = ttemp[7];
        rp = ttemp[8];
        rteosq = ttemp[9];
        sinio = ttemp[10];

        satrec.error = 0;

        
        
        






        if((omeosq >= 0.0) || (satrec.no >= 0.0))
        {
            satrec.isimp = 0;
            if(rp < (220.0 / radiusearthkm + 1.0))
            {
                satrec.isimp = 1;
            }
            sfour = ss;
            qzms24 = qzms2t;
            perige = (rp - 1.0) * radiusearthkm;

            
            if(perige < 156.0)
            {
                sfour = perige - 78.0;
                if(perige < 98.0)
                {
                    sfour = 20.0;
                }
                qzms24 = Math.pow(((120.0 - sfour) / radiusearthkm), 4.0);
                sfour = sfour / radiusearthkm + 1.0;
            }
            pinvsq = 1.0 / posq;

            tsi = 1.0 / (ao - sfour);
            satrec.eta = ao * satrec.ecco * tsi;
            etasq = satrec.eta * satrec.eta;
            eeta = satrec.ecco * satrec.eta;
            psisq = Math.abs(1.0 - etasq);
            coef = qzms24 * Math.pow(tsi, 4.0);
            coef1 = coef / Math.pow(psisq, 3.5);
            cc2 = coef1 * satrec.no * (ao * (1.0 + 1.5 * etasq + eeta *
                    (4.0 + etasq)) + 0.375 * j2 * tsi / psisq * satrec.con41 *
                    (8.0 + 3.0 * etasq * (8.0 + etasq)));
            satrec.cc1 = satrec.bstar * cc2;
            cc3 = 0.0;
            if(satrec.ecco > 1.0e-4)
            {
                cc3 = -2.0 * coef * tsi * j3oj2 * satrec.no * sinio / satrec.ecco;
            }
            satrec.x1mth2 = 1.0 - cosio2;
            satrec.cc4 = 2.0 * satrec.no * coef1 * ao * omeosq *
                    (satrec.eta * (2.0 + 0.5 * etasq) + satrec.ecco *
                    (0.5 + 2.0 * etasq) - j2 * tsi / (ao * psisq) *
                    (-3.0 * satrec.con41 * (1.0 - 2.0 * eeta + etasq *
                    (1.5 - 0.5 * eeta)) + 0.75 * satrec.x1mth2 *
                    (2.0 * etasq - eeta * (1.0 + etasq)) * Math.cos(2.0 * satrec.argpo)));
            satrec.cc5 = 2.0 * coef1 * ao * omeosq * (1.0 + 2.75 *
                    (etasq + eeta) + eeta * etasq);
            cosio4 = cosio2 * cosio2;
            temp1 = 1.5 * j2 * pinvsq * satrec.no;
            temp2 = 0.5 * temp1 * j2 * pinvsq;
            temp3 = -0.46875 * j4 * pinvsq * pinvsq * satrec.no;
            satrec.mdot = satrec.no + 0.5 * temp1 * rteosq * satrec.con41 + 0.0625 *
                    temp2 * rteosq * (13.0 - 78.0 * cosio2 + 137.0 * cosio4);
            satrec.argpdot = -0.5 * temp1 * con42 + 0.0625 * temp2 *
                    (7.0 - 114.0 * cosio2 + 395.0 * cosio4) +
                    temp3 * (3.0 - 36.0 * cosio2 + 49.0 * cosio4);
            xhdot1 = -temp1 * cosio;
            satrec.nodedot = xhdot1 + (0.5 * temp2 * (4.0 - 19.0 * cosio2) +
                    2.0 * temp3 * (3.0 - 7.0 * cosio2)) * cosio;
            xpidot = satrec.argpdot + satrec.nodedot;
            satrec.omgcof = satrec.bstar * cc3 * Math.cos(satrec.argpo);
            satrec.xmcof = 0.0;
            if(satrec.ecco > 1.0e-4)
            {
                satrec.xmcof = -x2o3 * coef * satrec.bstar / eeta;
            }
            satrec.nodecf = 3.5 * omeosq * xhdot1 * satrec.cc1;
            satrec.t2cof = 1.5 * satrec.cc1;
            
            if(Math.abs(cosio + 1.0) > 1.5e-12)
            {
                satrec.xlcof = -0.25 * j3oj2 * sinio * (3.0 + 5.0 * cosio) / (1.0 + cosio);
            }
            else
            {
                satrec.xlcof = -0.25 * j3oj2 * sinio * (3.0 + 5.0 * cosio) / temp4;
            }
            satrec.aycof = -0.5 * j3oj2 * sinio;
            satrec.delmo = Math.pow((1.0 + satrec.eta * Math.cos(satrec.mo)), 3.0);
            satrec.sinmao = Math.sin(satrec.mo);
            satrec.x7thm1 = 7.0 * cosio2 - 1.0;

            
            if((2 * pi / satrec.no) >= 225.0)
            {
                satrec.method = 'd';
                satrec.isimp = 1;
                tc = 0.0;
                inclm = satrec.inclo;

                var ttemp2: DoubleArray = dscom(epoch, satrec.ecco, satrec.argpo, tc, satrec.inclo, satrec.nodeo, satrec.no, satrec);
                
                snodm = ttemp2[0];
                cnodm = ttemp2[1];
                sinim = ttemp2[2];
                cosim = ttemp2[3];
                sinomm = ttemp2[4];
                cosomm = ttemp2[5];
                day = ttemp2[6];
                em = ttemp2[7];
                emsq = ttemp2[8];
                gam = ttemp2[9];
                rtemsq = ttemp2[10];
                s1 = ttemp2[11];
                s2 = ttemp2[12];
                s3 = ttemp2[13];
                s4 = ttemp2[14];
                s5 = ttemp2[15];
                s6 = ttemp2[16];
                s7 = ttemp2[17];
                ss1 = ttemp2[18];
                ss2 = ttemp2[19];
                ss3 = ttemp2[20];
                ss4 = ttemp2[21];
                ss5 = ttemp2[22];
                ss6 = ttemp2[23];
                ss7 = ttemp2[24];
                sz1 = ttemp2[25];
                sz2 = ttemp2[26];
                sz3 = ttemp2[27];
                sz11 = ttemp2[28];
                sz12 = ttemp2[29];
                sz13 = ttemp2[30];
                sz21 = ttemp2[31];
                sz22 = ttemp2[32];
                sz23 = ttemp2[33];
                sz31 = ttemp2[34];
                sz32 = ttemp2[35];
                sz33 = ttemp2[36];
                nm = ttemp2[37];
                z1 = ttemp2[38];
                z2 = ttemp2[39];
                z3 = ttemp2[40];
                z11 = ttemp2[41];
                z12 = ttemp2[42];
                z13 = ttemp2[43];
                z21 = ttemp2[44];
                z22 = ttemp2[45];
                z23 = ttemp2[46];
                z31 = ttemp2[47];
                z32 = ttemp2[48];
                z33 = ttemp2[49];

                
                ttemp2 = dpper(
                        satrec.e3, satrec.ee2, satrec.peo, satrec.pgho,
                        satrec.pho, satrec.pinco, satrec.plo, satrec.se2,
                        satrec.se3, satrec.sgh2, satrec.sgh3, satrec.sgh4,
                        satrec.sh2, satrec.sh3, satrec.si2, satrec.si3,
                        satrec.sl2, satrec.sl3, satrec.sl4, satrec.t,
                        satrec.xgh2, satrec.xgh3, satrec.xgh4, satrec.xh2,
                        satrec.xh3, satrec.xi2, satrec.xi3, satrec.xl2,
                        satrec.xl3, satrec.xl4, satrec.zmol, satrec.zmos, inclm, satrec.init,
                        satrec.ecco, satrec.inclo, satrec.nodeo, satrec.argpo, satrec.mo,
                        satrec.operationmode);
                satrec.ecco = ttemp2[0];
                satrec.inclo = ttemp2[1];
                satrec.nodeo = ttemp2[2];
                satrec.argpo = ttemp2[3];
                satrec.mo = ttemp2[4];

                argpm = 0.0;
                nodem = 0.0;
                mm = 0.0;

                var ttemp3: DoubleArray = dsinit(whichconst,
                        cosim, emsq, satrec.argpo, s1, s2, s3, s4, s5, sinim, ss1, ss2, ss3, ss4,
                        ss5, sz1, sz3, sz11, sz13, sz21, sz23, sz31, sz33, satrec.t, tc,
                        satrec.gsto, satrec.mo, satrec.mdot, satrec.no, satrec.nodeo,
                        satrec.nodedot, xpidot, z1, z3, z11, z13, z21, z23, z31, z33,
                        satrec.ecco, eccsq,
                        satrec,
                        em, argpm, inclm, mm, nm, nodem);
                em = ttemp3[0];
                argpm = ttemp3[1];
                inclm = ttemp3[2];
                mm = ttemp3[3];
                nm = ttemp3[4];
                nodem = ttemp3[5];
                dndt = ttemp3[6];
            }

            
            if(satrec.isimp != 1)
            {
                cc1sq = satrec.cc1 * satrec.cc1;
                satrec.d2 = 4.0 * ao * tsi * cc1sq;
                temp = satrec.d2 * tsi * satrec.cc1 / 3.0;
                satrec.d3 = (17.0 * ao + sfour) * temp;
                satrec.d4 = 0.5 * temp * ao * tsi * (221.0 * ao + 31.0 * sfour) *
                        satrec.cc1;
                satrec.t3cof = satrec.d2 + 2.0 * cc1sq;
                satrec.t4cof = 0.25 * (3.0 * satrec.d3 + satrec.cc1 *
                        (12.0 * satrec.d2 + 10.0 * cc1sq));
                satrec.t5cof = 0.2 * (3.0 * satrec.d4 +
                        12.0 * satrec.cc1 * satrec.d3 +
                        6.0 * satrec.d2 * satrec.d2 +
                        15.0 * cc1sq * (2.0 * satrec.d2 + cc1sq));
            }
        } 

        
        

        var sgp4Error: Boolean = sgp4(satrec, 0.0, r, v);  

        satrec.init = 'n';


        
        return sgp4Error;
    
    }
    fun sgp4(input_satrec: Sgp4Record, input_tsince: Double, input_r: DoubleArray, input_v: DoubleArray): Boolean {
        var satrec = input_satrec
        var tsince = input_tsince
        var r = input_r
        var v = input_v

        var am: Double = 0.0;
var axnl: Double = 0.0;
var aynl: Double = 0.0;
var betal: Double = 0.0;
var cosim: Double = 0.0;
var cnod: Double = 0.0;
var cos2u: Double = 0.0;
var coseo1: Double = 0.0;
var cosi: Double = 0.0;
var cosip: Double = 0.0;
var cosisq: Double = 0.0;
var cossu: Double = 0.0;
var cosu: Double = 0.0;
var delm: Double = 0.0;
var delomg: Double = 0.0;
var em: Double = 0.0;
var emsq: Double = 0.0;
var ecose: Double = 0.0;
var el2: Double = 0.0;
var eo1: Double = 0.0;
var ep: Double = 0.0;
var esine: Double = 0.0;
var argpm: Double = 0.0;
var argpp: Double = 0.0;
var argpdf: Double = 0.0;
var pl: Double = 0.0;
var mrt: Double = 0.0;
var mvt: Double = 0.0;
var rdotl: Double = 0.0;
var rl: Double = 0.0;
var rvdot: Double = 0.0;
var rvdotl: Double = 0.0;
var sinim: Double = 0.0;
var sin2u: Double = 0.0;
var sineo1: Double = 0.0;
var sini: Double = 0.0;
var sinip: Double = 0.0;
var sinsu: Double = 0.0;
var sinu: Double = 0.0;
var snod: Double = 0.0;
var su: Double = 0.0;
var t2: Double = 0.0;
var t3: Double = 0.0;
var t4: Double = 0.0;
var tem5: Double = 0.0;
var temp: Double = 0.0;
var temp1: Double = 0.0;
var temp2: Double = 0.0;
var tempa: Double = 0.0;
var tempe: Double = 0.0;
var templ: Double = 0.0;
var u: Double = 0.0;
var ux: Double = 0.0;
var uy: Double = 0.0;
var uz: Double = 0.0;
var vx: Double = 0.0;
var vy: Double = 0.0;
var vz: Double = 0.0;
var inclm: Double = 0.0;
var mm: Double = 0.0;
var nm: Double = 0.0;
var nodem: Double = 0.0;
var xinc: Double = 0.0;
var xincp: Double = 0.0;
var xl: Double = 0.0;
var xlm: Double = 0.0;
var mp: Double = 0.0;
var xmdf: Double = 0.0;
var xmx: Double = 0.0;
var xmy: Double = 0.0;
var nodedf: Double = 0.0;
var xnode: Double = 0.0;
var nodep: Double = 0.0;
var tc: Double = 0.0;
var dndt: Double = 0.0;
var twopi: Double = 0.0;
var x2o3: Double = 0.0;
var j2: Double = 0.0;
var j3: Double = 0.0;
var tumin: Double = 0.0;
var j4: Double = 0.0;
var xke: Double = 0.0;
var j3oj2: Double = 0.0;
var radiusearthkm: Double = 0.0;
var mu: Double = 0.0;
var vkmpersec: Double = 0.0;
        var ktr: Int = 0;

        
        
        
        
        var temp4: Double = 1.5e-12;
        twopi = 2.0 * pi;
        x2o3 = 2.0 / 3.0;
        
        var temp5: DoubleArray = getgravconst(satrec.gravconsttype);
        tumin = temp5[0];
        mu = temp5[1];
        radiusearthkm = temp5[2];
        xke = temp5[3];
        j2 = temp5[4];
        j3 = temp5[5];
        j4 = temp5[6];
        j3oj2 = temp5[7];

        vkmpersec = radiusearthkm * xke / 60.0;

        
        satrec.t = tsince;
        satrec.error = 0;

        
        xmdf = satrec.mo + satrec.mdot * satrec.t;
        argpdf = satrec.argpo + satrec.argpdot * satrec.t;
        nodedf = satrec.nodeo + satrec.nodedot * satrec.t;
        argpm = argpdf;
        mm = xmdf;
        t2 = satrec.t * satrec.t;
        nodem = nodedf + satrec.nodecf * t2;
        tempa = 1.0 - satrec.cc1 * satrec.t;
        tempe = satrec.bstar * satrec.cc4 * satrec.t;
        templ = satrec.t2cof * t2;

        if(satrec.isimp != 1)
        {
            delomg = satrec.omgcof * satrec.t;
            delm = satrec.xmcof *
                    (Math.pow((1.0 + satrec.eta * Math.cos(xmdf)), 3.0) -
                    satrec.delmo);
            temp = delomg + delm;
            mm = xmdf + temp;
            argpm = argpdf - temp;
            t3 = t2 * satrec.t;
            t4 = t3 * satrec.t;
            tempa = tempa - satrec.d2 * t2 - satrec.d3 * t3 -
                    satrec.d4 * t4;
            tempe = tempe + satrec.bstar * satrec.cc5 * (Math.sin(mm) -
                    satrec.sinmao);
            templ = templ + satrec.t3cof * t3 + t4 * (satrec.t4cof +
                    satrec.t * satrec.t5cof);
        }

        nm = satrec.no;
        em = satrec.ecco;
        inclm = satrec.inclo;
        if(satrec.method == 'd')
        {
            tc = satrec.t;
            var ttemp: DoubleArray = dspace(
                    satrec.irez,
                    satrec.d2201, satrec.d2211, satrec.d3210,
                    satrec.d3222, satrec.d4410, satrec.d4422,
                    satrec.d5220, satrec.d5232, satrec.d5421,
                    satrec.d5433, satrec.dedt, satrec.del1,
                    satrec.del2, satrec.del3, satrec.didt,
                    satrec.dmdt, satrec.dnodt, satrec.domdt,
                    satrec.argpo, satrec.argpdot, satrec.t, tc,
                    satrec.gsto, satrec.xfact, satrec.xlamo,
                    satrec.no,
                    em, argpm, inclm, mm, nodem,
                    satrec,
                    nm);
            
            em = ttemp[0];
            argpm = ttemp[1];
            inclm = ttemp[2];
            mm = ttemp[3];
            nodem = ttemp[4];
            dndt = ttemp[5];
            nm = ttemp[6];

        } 

        if(nm <= 0.0)
        {

            satrec.error = 2;
            
            return false;
        }
        am = Math.pow((xke / nm), x2o3) * tempa * tempa;
        nm = xke / Math.pow(am, 1.5);
        em = em - tempe;

        
        
        if((em >= 1.0) || (em < -0.001))
        {

            satrec.error = 1;
            
            return false;
        }
        
        if(em < 1.0e-6)
        {
            em = 1.0e-6;
        }
        mm = mm + satrec.no * templ;
        xlm = mm + argpm + nodem;
        emsq = em * em;
        temp = 1.0 - emsq;

        nodem = (nodem % twopi);
        argpm = (argpm % twopi);
        xlm = (xlm % twopi);
        mm = ((xlm - argpm - nodem) % twopi);

        
        sinim = Math.sin(inclm);
        cosim = Math.cos(inclm);

        
        ep = em;
        xincp = inclm;
        argpp = argpm;
        nodep = nodem;
        mp = mm;
        sinip = sinim;
        cosip = cosim;
        if(satrec.method == 'd')
        {
            
            var ttemp: DoubleArray = dpper(
                    satrec.e3, satrec.ee2, satrec.peo,
                    satrec.pgho, satrec.pho, satrec.pinco,
                    satrec.plo, satrec.se2, satrec.se3,
                    satrec.sgh2, satrec.sgh3, satrec.sgh4,
                    satrec.sh2, satrec.sh3, satrec.si2,
                    satrec.si3, satrec.sl2, satrec.sl3,
                    satrec.sl4, satrec.t, satrec.xgh2,
                    satrec.xgh3, satrec.xgh4, satrec.xh2,
                    satrec.xh3, satrec.xi2, satrec.xi3,
                    satrec.xl2, satrec.xl3, satrec.xl4,
                    satrec.zmol, satrec.zmos, satrec.inclo,
                    'n', ep, xincp, nodep, argpp, mp, satrec.operationmode);
            ep = ttemp[0];
            xincp = ttemp[1];
            nodep = ttemp[2];
            argpp = ttemp[3];
            mp = ttemp[4];

            if(xincp < 0.0)
            {
                xincp = -xincp;
                nodep = nodep + pi;
                argpp = argpp - pi;
            }
            if((ep < 0.0) || (ep > 1.0))
            {

                satrec.error = 3;
                
                return false;
            }
        } 

        
        if(satrec.method == 'd')
        {
            sinip = Math.sin(xincp);
            cosip = Math.cos(xincp);
            satrec.aycof = -0.5 * j3oj2 * sinip;
            
            if(Math.abs(cosip + 1.0) > 1.5e-12)
            {
                satrec.xlcof = -0.25 * j3oj2 * sinip * (3.0 + 5.0 * cosip) / (1.0 + cosip);
            }
            else
            {
                satrec.xlcof = -0.25 * j3oj2 * sinip * (3.0 + 5.0 * cosip) / temp4;
            }
        }
        axnl = ep * Math.cos(argpp);
        temp = 1.0 / (am * (1.0 - ep * ep));
        aynl = ep * Math.sin(argpp) + temp * satrec.aycof;
        xl = mp + argpp + nodep + temp * satrec.xlcof * axnl;

        
        u = ((xl - nodep) % twopi);
        eo1 = u;
        tem5 = 9999.9;
        ktr = 1;
        
        
        while((Math.abs(tem5) >= 1.0e-12) && (ktr <= 10))
        {
            sineo1 = Math.sin(eo1);
            coseo1 = Math.cos(eo1);
            tem5 = 1.0 - coseo1 * axnl - sineo1 * aynl;
            tem5 = (u - aynl * coseo1 + axnl * sineo1 - eo1) / tem5;
            if(Math.abs(tem5) >= 0.95)
            {
                tem5 = if(tem5 > 0.0) 0.95 else -0.95;
            }
            eo1 = eo1 + tem5;
            ktr = ktr + 1;
        }

        
        ecose = axnl * coseo1 + aynl * sineo1;
        esine = axnl * sineo1 - aynl * coseo1;
        el2 = axnl * axnl + aynl * aynl;
        pl = am * (1.0 - el2);
        if(pl < 0.0)
        {

            satrec.error = 4;
            
            return false;
        }
        else
        {
            rl = am * (1.0 - ecose);
            rdotl = Math.sqrt(am) * esine / rl;
            rvdotl = Math.sqrt(pl) / rl;
            betal = Math.sqrt(1.0 - el2);
            temp = esine / (1.0 + betal);
            sinu = am / rl * (sineo1 - aynl - axnl * temp);
            cosu = am / rl * (coseo1 - axnl + aynl * temp);
            su = Math.atan2(sinu, cosu);
            sin2u = (cosu + cosu) * sinu;
            cos2u = 1.0 - 2.0 * sinu * sinu;
            temp = 1.0 / pl;
            temp1 = 0.5 * j2 * temp;
            temp2 = temp1 * temp;

            
            if(satrec.method == 'd')
            {
                cosisq = cosip * cosip;
                satrec.con41 = 3.0 * cosisq - 1.0;
                satrec.x1mth2 = 1.0 - cosisq;
                satrec.x7thm1 = 7.0 * cosisq - 1.0;
            }
            mrt = rl * (1.0 - 1.5 * temp2 * betal * satrec.con41) +
                    0.5 * temp1 * satrec.x1mth2 * cos2u;
            su = su - 0.25 * temp2 * satrec.x7thm1 * sin2u;
            xnode = nodep + 1.5 * temp2 * cosip * sin2u;
            xinc = xincp + 1.5 * temp2 * cosip * sinip * cos2u;
            mvt = rdotl - nm * temp1 * satrec.x1mth2 * sin2u / xke;
            rvdot = rvdotl + nm * temp1 * (satrec.x1mth2 * cos2u +
                    1.5 * satrec.con41) / xke;

            
            sinsu = Math.sin(su);
            cossu = Math.cos(su);
            snod = Math.sin(xnode);
            cnod = Math.cos(xnode);
            sini = Math.sin(xinc);
            cosi = Math.cos(xinc);
            xmx = -snod * cosi;
            xmy = cnod * cosi;
            ux = xmx * sinsu + cnod * cossu;
            uy = xmy * sinsu + snod * cossu;
            uz = sini * sinsu;
            vx = xmx * cossu - cnod * sinsu;
            vy = xmy * cossu - snod * sinsu;
            vz = sini * cossu;

            
            r[0] = (mrt * ux) * radiusearthkm;
            r[1] = (mrt * uy) * radiusearthkm;
            r[2] = (mrt * uz) * radiusearthkm;
            v[0] = (mvt * ux + rvdot * vx) * vkmpersec;
            v[1] = (mvt * uy + rvdot * vy) * vkmpersec;
            v[2] = (mvt * uz + rvdot * vz) * vkmpersec;
        }  

        
        if(mrt < 1.0)
        {

            satrec.error = 6;
            return false;
        }


        return true;
    
    }
    fun gstime(input_jdut1: Double): Double {
        var jdut1 = input_jdut1

        var twopi: Double = 2.0 * pi;
        var deg2rad: Double = pi / 180.0;
        var temp: Double = 0.0;
var tut1: Double = 0.0;

        tut1 = (jdut1 - 2451545.0) / 36525.0;
        temp = -6.2e-6 * tut1 * tut1 * tut1 + 0.093104 * tut1 * tut1 +
                (876600.0 * 3600 + 8640184.812866) * tut1 + 67310.54841;  
        temp = ((temp * deg2rad / 240.0) % twopi); 

        
        if(temp < 0.0)
        {
            temp += twopi;
        }

        return temp;
    
    }
    private val gravityCache = Gravconsttype.entries.associateWith { calculateGravity(it) }
    fun getgravconst(input_whichconst: Gravconsttype): DoubleArray = gravityCache.getValue(input_whichconst)
    private fun calculateGravity(input_whichconst: Gravconsttype): DoubleArray {
        var whichconst = input_whichconst


        
        var tumin: Double = 0.0;
var mu: Double = 0.0;
var radiusearthkm: Double = 0.0;
var xke: Double = 0.0;
var j2: Double = 0.0;
var j3: Double = 0.0;
var j4: Double = 0.0;
var j3oj2: Double = 0.0;

        when(whichconst)
        {
            
            Gravconsttype.wgs72old -> {
                mu = 398600.79964;        
                radiusearthkm = 6378.135;     
                xke = 0.0743669161;
                tumin = 1.0 / xke;
                j2 = 0.001082616;
                j3 = -0.00000253881;
                j4 = -0.00000165597;
                j3oj2 = j3 / j2;
                }
            
            Gravconsttype.wgs72 -> {
                mu = 398600.8;            
                radiusearthkm = 6378.135;     
                xke = 60.0 / Math.sqrt(radiusearthkm * radiusearthkm * radiusearthkm / mu);
                tumin = 1.0 / xke;
                j2 = 0.001082616;
                j3 = -0.00000253881;
                j4 = -0.00000165597;
                j3oj2 = j3 / j2;
                }
            Gravconsttype.wgs84 -> {
                
                mu = 398600.5;            
                radiusearthkm = 6378.137;     
                xke = 60.0 / Math.sqrt(radiusearthkm * radiusearthkm * radiusearthkm / mu);
                tumin = 1.0 / xke;
                j2 = 0.00108262998905;
                j3 = -0.00000253215306;
                j4 = -0.00000161098761;
                j3oj2 = j3 / j2;
                }
            
}

        return doubleArrayOf(
                    tumin, mu, radiusearthkm, xke, j2, j3, j4, j3oj2
                );

    
    }
}
