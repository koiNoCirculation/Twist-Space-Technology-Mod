package com.Nxer.TwistSpaceTechnology.common.machine;

import static com.github.technus.tectech.thing.metaTileEntity.multi.base.GT_MetaTileEntity_MultiblockBase_EM.HatchElement.DynamoMulti;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GT_HatchElement.Dynamo;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASING_DRAGONEGG;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASING_DRAGONEGG_GLOW;
import static gregtech.api.util.GT_StructureUtility.ofFrame;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.Nxer.TwistSpaceTechnology.common.block.BasicBlocks;
import com.Nxer.TwistSpaceTechnology.util.TextLocalization;
import com.emoniph.witchery.Witchery;
import com.github.technus.tectech.thing.casing.TT_Container_Casings;
import com.github.technus.tectech.thing.metaTileEntity.hatch.GT_MetaTileEntity_Hatch_DynamoMulti;
import com.github.technus.tectech.thing.metaTileEntity.hatch.GT_MetaTileEntity_Hatch_DynamoTunnel;
import com.github.technus.tectech.thing.metaTileEntity.multi.base.GT_MetaTileEntity_MultiblockBase_EM;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import goodgenerator.loader.Loaders;
import gregtech.api.GregTech_API;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.GT_MetaTileEntity_Hatch_Dynamo;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GT_HatchElementBuilder;
import gregtech.api.util.GT_Multiblock_Tooltip_Builder;
import micdoodle8.mods.galacticraft.planets.mars.blocks.MarsBlocks;

public class GT_TileEntity_MegaEggGenerator extends GT_MetaTileEntity_MultiblockBase_EM
    implements IConstructable, ISurvivalConstructable {

    // region Class Constructor
    public GT_TileEntity_MegaEggGenerator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public GT_TileEntity_MegaEggGenerator(String aName) {
        super(aName);
    }
    // endregion

    // region Processing Logic
    private int mPieces = 1;
    private int mInfinityEggs = 0;
    private int mDragonEggs = 0;
    private int mCrepperEggs = 0;
    private int mAirPosed = 0;

    @Override
    @NotNull
    public CheckRecipeResult checkProcessing_EM() {
        this.mMaxProgresstime = 20;
        getEfficiencyIncrease();
        getOutput();
        return CheckRecipeResultRegistry.GENERATING;
    }

    /**
     * When Infinity eggs exist, every infinity egg give 100t efficiency increase boost.
     */
    private void getEfficiencyIncrease() {
        if (mInfinityEggs != 0) {
            this.mEfficiencyIncrease = mInfinityEggs * 100;
        } else this.mEfficiencyIncrease = 1;
    }

    /**
     * 1A HV Crepper egg
     * 1A EV Dragon egg
     * 2A IV Infinite egg
     * Can be defined in config
     * 2024.1.21 Fix vol explode
     */
    private void getOutput() {
        long vol = 0, amp = 0, tOutput = 0;
        for (GT_MetaTileEntity_Hatch_Dynamo tHatch : mDynamoHatches) {
            vol += tHatch.maxEUOutput();
            amp += tHatch.maxAmperesOut();
        }
        for (GT_MetaTileEntity_Hatch_DynamoMulti tHatch : eDynamoMulti) {
            vol += tHatch.maxEUOutput();
            amp += tHatch.maxAmperesOut();
        }
        if (vol > Integer.MAX_VALUE) vol = Integer.MAX_VALUE;
        if (amp > Integer.MAX_VALUE) amp = Integer.MAX_VALUE;

        tOutput = (long) (ValueEnum.MEG_Overall_Multiply
            * (ValueEnum.MEG_CrepperEgg_Gen * mCrepperEggs + ValueEnum.MEG_DragonEgg_Gen * mDragonEggs
                + ValueEnum.MEG_InfinityEgg_Gen * mInfinityEggs));
        if (tOutput > vol) {
            mEUt = (int) vol;
            eAmpereFlow = Math.min(tOutput / vol, amp);
        } else {
            mEUt = (int) tOutput;
        }
    }

    /**
     * 2% max efficiency buff every 2^n pieces.
     * 1% max efficiency every 1 infinity egg.
     */
    private int getCalculatedEfficiency() {
        return Math
            .max(
                0,
                10000 + ValueEnum.MEG_Efficiency_PiecesBuff * (int) (Math.log(mPieces) / Math.log(2))
                    - ValueEnum.MEG_Efficiency_Lost * mAirPosed)
            + ValueEnum.MEG_Efficiency_InfinityEggBuff * mInfinityEggs;
    }

    @Override
    public boolean checkMachine_EM(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        // No need for the maintenance hatches!
        turnOffMaintenance();
        // Initialize
        this.mInfinityEggs = 0;
        this.mDragonEggs = 0;
        this.mCrepperEggs = 0;
        this.mAirPosed = 0;
        this.mPieces = 0;
        // Main checks
        return checkAllPieces() && checkInfinityEgg() && checkLaser() && checkDynamo();
    }

    /**
     * Check main structure of pieces.
     *
     * @return If pieces are all set
     */
    private boolean checkAllPieces() {
        if (!checkPiece(STRUCTURE_PIECE_BASE, horizontalOffSet, verticalOffSet, depthOffSet)) {
            return false;
        }
        while (checkPiece(
            STRUCTURE_PIECE_MIDDLE,
            horizontalOffSet,
            verticalOffSet + (this.mPieces + 1) * 2,
            depthOffSet)) {
            this.mPieces++;
        }
        if (mPieces < 1) {
            return false;
        }
        return checkPiece(STRUCTURE_PIECE_TOP, horizontalOffSet, verticalOffSet + this.mPieces * 2 + 1, depthOffSet);
    }

    /**
     * Check Infinity egg num.
     *
     * @return If Infinity egg num is allowed
     */
    private boolean checkInfinityEgg() {
        return mInfinityEggs <= mPieces;
    }

    /**
     * Check Laser hatch when pieces are fewer than 16.
     *
     * @return If laser is allowed
     */
    private boolean checkLaser() {
        if (mPieces < ValueEnum.MEG_Laser_Pieces) {
            for (GT_MetaTileEntity_Hatch_DynamoMulti tHatch : eDynamoMulti) {
                if (tHatch instanceof GT_MetaTileEntity_Hatch_DynamoTunnel) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * No matter what kind of dynamo in use, only one allowed.
     *
     * @return If dynamo is allowed
     */
    private boolean checkDynamo() {
        return (mDynamoHatches.size() + eDynamoMulti.size()) == ValueEnum.MEG_Dynamo_Limit;
    }

    // endregion

    // region Structure
    // spotless:off
	@Override
	public void construct(ItemStack stackSize, boolean hintsOnly) {
		this.buildPiece(STRUCTURE_PIECE_BASE, stackSize, hintsOnly, horizontalOffSet, verticalOffSet, depthOffSet);
		int piece = stackSize.stackSize;
		for (int i=1; i<=piece; i++){
			this.buildPiece(STRUCTURE_PIECE_MIDDLE, stackSize, hintsOnly, horizontalOffSet, verticalOffSet + i * 2, depthOffSet);
		}
		this.buildPiece(STRUCTURE_PIECE_TOP, stackSize, hintsOnly, horizontalOffSet, verticalOffSet + piece * 2 +1, depthOffSet);
	}

	@Override
    @SuppressWarnings("deprecation")
	public int survivalConstruct(ItemStack stackSize, int elementBudget, IItemSource source, EntityPlayerMP actor){
		if (this.mMachine) return -1;
		int built = 0;

		built += survivialBuildPiece(
            STRUCTURE_PIECE_BASE,
			stackSize,
			horizontalOffSet,
			verticalOffSet,
			depthOffSet,
			elementBudget,
			source,
			actor,
			false,
			true);

		int piece = stackSize.stackSize;
		for (int i=1; i<=piece; i++){
			built += survivialBuildPiece(
				STRUCTURE_PIECE_MIDDLE,
				stackSize,
				horizontalOffSet,
				verticalOffSet + i * 2,
				depthOffSet,
				elementBudget,
				source,
				actor,
				false,
				true);
		}

		built += survivialBuildPiece(
            STRUCTURE_PIECE_TOP,
			stackSize,
			horizontalOffSet,
			verticalOffSet + piece * 2 + 1,
			depthOffSet,
			elementBudget,
			source,
			actor,
			false,
			true);

		return built;
	}

	private IStructureDefinition<GT_TileEntity_MegaEggGenerator> structureDef = null;
    private static final String STRUCTURE_PIECE_BASE = "baseEggGenerator";
	private static final String STRUCTURE_PIECE_MIDDLE = "middleEggGenerator";
	private static final String STRUCTURE_PIECE_TOP = "topEggGenerator";
	private final int horizontalOffSet = 7;
	private final int verticalOffSet = 0;
	private final int depthOffSet = 6;

    /**
     * I have to add this shit to deal with runtime conflicts that have nothing to do with TST.
     */
    private Block InfinityEgg() {
        if (Mods.Witchery.isModLoaded()) {
            return Witchery.Blocks.INFINITY_EGG;
        } else return BasicBlocks.PhotonControllerUpgrade;
    }

	@Override
	public IStructureDefinition<GT_TileEntity_MegaEggGenerator> getStructure_EM() {
        if (structureDef == null) {
		structureDef = StructureDefinition.<GT_TileEntity_MegaEggGenerator>builder()
			       .addShape(STRUCTURE_PIECE_BASE, transpose(shapeBase))
			       .addShape(STRUCTURE_PIECE_MIDDLE, transpose(shapeMiddle))
			       .addShape(STRUCTURE_PIECE_TOP, transpose(shapeTop))
			       .addElement('A',
                       GT_HatchElementBuilder.<GT_TileEntity_MegaEggGenerator>builder()
                                       .atLeast(Dynamo.or(DynamoMulti))
                           .adder(GT_TileEntity_MegaEggGenerator::addToMachineList)
                                       .casingIndex(1536)
                                       .dot(1)
                                       .buildAndChain(Loaders.magicCasing, 0))
			       .addElement('B',ofFrame(Materials.Trinium))
			       .addElement('C',ofBlock(Loaders.essentiaCell, 0))
			       .addElement('D',ofFrame(Materials.Iridium))
			       .addElement('F',ofBlock(TT_Container_Casings.sBlockCasingsBA0, 8))
			       .addElement('G',ofBlock(GregTech_API.sBlockCasings8, 7))
			       .addElement('K',
                       ofChain(
                           onElementPass(k -> ++k.mAirPosed, isAir()),
                           onElementPass(k -> ++k.mCrepperEggs, ofBlock(MarsBlocks.creeperEgg, 0)),
                           onElementPass(k -> ++k.mDragonEggs, ofBlock(Blocks.dragon_egg,0)),
                           onElementPass(k -> ++k.mInfinityEggs, ofBlock(InfinityEgg(), 0))
                       )
                   )
			       .build();}
        return structureDef;
	}

	private final String[][] shapeBase = new String[][]{
        {"               ","      AAA      ","    AA   AA    ","   A       A   ","  A         A  ","  A         A  "," A    G~G    A "," A    GGG    A "," A    GGG    A ","  A         A  ","  A         A  ","   A       A   ","    AA   AA    ","      AAA      ","               "}
    };
	private final String[][] shapeMiddle = new String[][]{
        {"               ","      AAA      ","    AAK KAA    ","   AK     KA   ","  AK       KA  ","  A         A  "," AK    D    KA "," A    DFD    A "," AK    D    KA ","  A         A  ","  AK       KA  ","   AK     KA   ","    AAK KAA    ","      AAA      ","               "},
        {"      AAA      ","    AABBBAA    ","   ABBCACBBA   ","  ABCA   ACBA  "," ABC       CBA "," ABA       ABA ","ABC    D    CBA","ABA   DFD   ABA","ABC    D    CBA"," ABA       ABA "," ABC       CBA ","  ABCA   ACBA  ","   ABBCACBBA   ","    AABBBAA    ","      AAA      "}
    };
	private final String[][] shapeTop = new String[][]{
        {"               ","               ","               ","               ","               ","               ","               ","       F       ","               ","               ","               ","               ","               ","               ","               "}
    };

	// spotless:on
    @Override
    public boolean isRotationChangeAllowed() {
        return ValueEnum.MEG_AllowRotation;
    }
    // endregion

    // region Overrides

    @Override
    public String[] getInfoData() {
        String[] origin = super.getInfoData();
        String[] ret = new String[origin.length + 5];
        ret[origin.length] = EnumChatFormatting.AQUA + "Infinity Eggs: " + EnumChatFormatting.GOLD + this.mInfinityEggs;
        ret[origin.length + 1] = EnumChatFormatting.AQUA + "Dragon Eggs: " + EnumChatFormatting.GOLD + this.mDragonEggs;
        ret[origin.length + 2] = EnumChatFormatting.AQUA + "Crepper Eggs: "
            + EnumChatFormatting.GOLD
            + this.mCrepperEggs;
        ret[origin.length + 3] = EnumChatFormatting.AQUA + "Air Voids: " + EnumChatFormatting.GOLD + this.mAirPosed;
        ret[origin.length + 4] = EnumChatFormatting.AQUA + "Pieces: " + EnumChatFormatting.GOLD + this.mPieces;
        return ret;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mInfinityEggs", mInfinityEggs);
        aNBT.setInteger("mDragonEggs", mDragonEggs);
        aNBT.setInteger("mCrepperEggs", mCrepperEggs);
        aNBT.setInteger("mAirPosed", mAirPosed);
        aNBT.setInteger("mPieces", mPieces);
    }

    @Override
    public void loadNBTData(final NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mInfinityEggs = aNBT.getInteger("mInfinityEggs");
        mDragonEggs = aNBT.getInteger("mDragonEggs");
        mCrepperEggs = aNBT.getInteger("mCrepperEggs");
        mAirPosed = aNBT.getInteger("mAirPosed");
        mPieces = aNBT.getInteger("mPieces");
    }

    @Override
    protected GT_Multiblock_Tooltip_Builder createTooltip() {
        final GT_Multiblock_Tooltip_Builder tt = new GT_Multiblock_Tooltip_Builder();
        tt.addMachineType(TextLocalization.Tooltip_MegaEggGenerator_MachineType)
            .addInfo(TextLocalization.Tooltip_MegaEggGenerator_Controller)
            .addInfo(TextLocalization.Tooltip_MegaEggGenerator_00)
            .addInfo(TextLocalization.Tooltip_MegaEggGenerator_01)
            .addInfo(TextLocalization.Tooltip_MegaEggGenerator_02)
            .addInfo(TextLocalization.Tooltip_MegaEggGenerator_03)
            .addInfo(TextLocalization.Tooltip_MegaEggGenerator_04)
            .addInfo(TextLocalization.Tooltip_MegaEggGenerator_05)
            .addInfo(TextLocalization.Tooltip_MegaEggGenerator_06)
            .addInfo(TextLocalization.Tooltip_MegaEggGenerator_07)
            .addInfo(TextLocalization.Tooltip_MegaEggGenerator_08)
            .addSeparator()
            .addInfo(TextLocalization.StructureTooComplex)
            .addInfo(TextLocalization.BLUE_PRINT_INFO)
            .addOtherStructurePart(
                TextLocalization.Tooltip_MegaEggGenerator_D,
                TextLocalization.Tooltip_MegaEggGenerator_C,
                1)
            .addStructureInfo(TextLocalization.Tooltip_MegaEggGenerator_L)
            .addStructureInfo(TextLocalization.Tooltip_MegaEggGenerator_M)
            .toolTipFinisher(TextLocalization.ModName);
        return tt;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return getCalculatedEfficiency();
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new GT_TileEntity_MegaEggGenerator(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(183),
                TextureFactory.builder()
                    .addIcon(MACHINE_CASING_DRAGONEGG_GLOW)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(MACHINE_CASING_DRAGONEGG_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(183), TextureFactory.builder()
                .addIcon(MACHINE_CASING_DRAGONEGG)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(MACHINE_CASING_DRAGONEGG)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(183) };
    }

    // endregion
}
