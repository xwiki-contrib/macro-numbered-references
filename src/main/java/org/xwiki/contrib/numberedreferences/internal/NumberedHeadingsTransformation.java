/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.numberedreferences.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.IdBlock;
import org.xwiki.rendering.block.SpaceBlock;
import org.xwiki.rendering.block.SpecialSymbolBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.match.BlockMatcher;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.transformation.TransformationContext;
import org.xwiki.rendering.transformation.TransformationException;

/**
 * Find all headings, create numbers (and support nested numbering with the dot notation, e.g. {@code 1.1.1.1}) for
 * them and display the number in front of the heading label.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("numberedheadings")
@Singleton
public class NumberedHeadingsTransformation extends AbstractNumberedTransformation
{
    private static final String CLASS = "class";

    private static final String CLASS_VALUE = "wikigeneratedheadingnumber";

    private static final BlockMatcher HEADINGBLOCK_MATCHER = new ClassBlockMatcher(HeaderBlock.class);

    private static final SpecialSymbolBlock DOT_BLOCK = new SpecialSymbolBlock('.');

    @Override
    public void transform(Block block, TransformationContext context) throws TransformationException
    {
        // Algorithm:
        // - Find all HeaderBlock (except those in protected data such as inside code macro)
        // - For each HeaderBlock, compute the heading number and cache it for later use for resolving the
        //   ReferenceBlock (generated by the Reference Macro). Then update the HeaderBlock children content by
        //   adding the number in front of the heading text.
        // - Also find all the IdBlock blocks inside HeaderBlock children and associate the ids with the numbers, so
        //   that the Reference Macro can use not only the generated header id but also any id contributed by the id
        //   macro.
        // - Find all the ReferenceBlock blocks and replace them with LinkBlock bocks to create links to numbered
        //   sections, using the number as the link label.

        Map<String, List<Block>> headingNumbers = new HashMap<>();
        Stack<Integer> number = new Stack<>();
        List<HeaderBlock> headerBlocks = block.getBlocks(HEADINGBLOCK_MATCHER, Block.Axes.DESCENDANT);
        for (HeaderBlock headerBlock : headerBlocks) {

            if (headerBlock.getChildren().isEmpty() || isInsProtectedBlock(headerBlock)) {
                continue;
            }

            // Step 1: Update the number stack to compute the new number
            int currentHeaderLevel = headerBlock.getLevel().getAsInt();
            if (number.size() < currentHeaderLevel) {
                int size = number.size();
                for (int i = 0; i < currentHeaderLevel - size; i++) {
                    number.push(1);
                }
            } else if (number.size() == currentHeaderLevel) {
                number.push(number.pop() + 1);
            } else {
                int size = number.size();
                for (int i = 0; i < size - currentHeaderLevel; i++) {
                    number.pop();
                }
                number.push(number.pop() + 1);
            }

            // Step 2: Insert the number in the header
            // Start by adding a space so that we have <number><space><rest of what was there before>
            headerBlock.insertChildBefore(new SpaceBlock(), headerBlock.getChildren().get(0));
            headerBlock.insertChildBefore(serializeAndFormatNumber(number), headerBlock.getChildren().get(0));

            // Step 3: Save in our cache the ids representing this section. We save the following keys in the cache:
            // - the header block id
            // - all the IdBlock found as children Blocks of the header block
            if (headerBlock.getId() != null) {
                headingNumbers.put(headerBlock.getId(), serializeNumber(number));
            }
            List<Block> idBlocks = headerBlock.getBlocks(new ClassBlockMatcher(IdBlock.class), Block.Axes.DESCENDANT);
            for (Block idBlock : idBlocks) {
                headingNumbers.put(((IdBlock) idBlock).getName(), serializeNumber(number));
            }
        }

        // Step 4: Replace the ReferenceBlock with links
        replaceReferenceBlocks(block, headingNumbers, "section");
    }

    private List<Block> serializeNumber(Stack<Integer> number)
    {
        List<Block> valueBlocks = new ArrayList<>();
        Iterator<Integer> iterator = number.iterator();
        while (iterator.hasNext()) {
            valueBlocks.add(new WordBlock(String.valueOf(iterator.next())));
            if (iterator.hasNext()) {
                valueBlocks.add(DOT_BLOCK);
            }
        }
        return valueBlocks;
    }

    private Block serializeAndFormatNumber(Stack<Integer> number)
    {
        return new FormatBlock(serializeNumber(number), Format.NONE, Collections.singletonMap(CLASS, CLASS_VALUE));
    }
}
