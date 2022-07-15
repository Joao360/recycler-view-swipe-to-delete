package net.nemanjakovacevic.recyclerviewswipetodelete

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration

/**
 * Sample activity demonstrating swipe to remove on recycler view functionality.
 * The interesting parts are drawing while items are animating to their new positions after some items is removed
 * and a possibility to undo the removal.
 */
class MainActivity : AppCompatActivity() {
    var mRecyclerView: RecyclerView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        mRecyclerView = findViewById<View>(R.id.recycler_view) as RecyclerView
        setUpRecyclerView()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_undo_checkbox) {
            item.isChecked = !item.isChecked
            (mRecyclerView!!.adapter as TestAdapter?)!!.isUndoOn = item.isChecked
        }
        if (item.itemId == R.id.menu_item_add_5_items) {
            (mRecyclerView!!.adapter as TestAdapter?)!!.addItems(5)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setUpRecyclerView() {
        mRecyclerView!!.layoutManager = LinearLayoutManager(this)
        mRecyclerView!!.adapter = TestAdapter()
        mRecyclerView!!.setHasFixedSize(true)
        setUpItemTouchHelper()
        setUpAnimationDecoratorHelper()
    }

    /**
     * This is the standard support library way of implementing "swipe to delete" feature. You can do custom drawing in onChildDraw method
     * but whatever you draw will disappear once the swipe is over, and while the items are animating to their new position the recycler view
     * background will be visible. That is rarely an desired effect.
     */
    private fun setUpItemTouchHelper() {
        val simpleItemTouchCallback: ItemTouchHelper.SimpleCallback =
            object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                // we want to cache these and not allocate anything repeatedly in the onChildDraw method
                var background: Drawable? = null
                var xMark: Drawable? = null
                var xMarkMargin = 0
                var initiated = false

                private fun init() {
                    background = ColorDrawable(Color.RED)
                    xMark = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_clear_24dp)
                    xMark!!.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                    xMarkMargin =
                        this@MainActivity.resources.getDimension(R.dimen.ic_clear_margin).toInt()
                    initiated = true
                }

                // not important, we don't want drag & drop
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                override fun getSwipeDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    val position = viewHolder.adapterPosition
                    val testAdapter = recyclerView.adapter as TestAdapter?
                    return if (testAdapter!!.isUndoOn && testAdapter.isPendingRemoval(position)) {
                        0
                    } else super.getSwipeDirs(recyclerView, viewHolder)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
                    val swipedPosition = viewHolder.adapterPosition
                    val adapter = mRecyclerView!!.adapter as TestAdapter?
                    val undoOn = adapter!!.isUndoOn
                    if (undoOn) {
                        adapter.pendingRemoval(swipedPosition)
                    } else {
                        adapter.remove(swipedPosition)
                    }
                }

                override fun onChildDraw(
                    c: Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    val itemView = viewHolder.itemView

                    // not sure why, but this method get's called for viewholder that are already swiped away
                    if (viewHolder.adapterPosition == -1) {
                        // not interested in those
                        return
                    }
                    if (!initiated) {
                        init()
                    }

                    // draw red background
                    background!!.setBounds(
                        itemView.right + dX.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                    background!!.draw(c)

                    // draw x mark
                    val itemHeight = itemView.bottom - itemView.top
                    val intrinsicWidth = xMark!!.intrinsicWidth
                    val intrinsicHeight = xMark!!.intrinsicWidth
                    val xMarkLeft = itemView.right - xMarkMargin - intrinsicWidth
                    val xMarkRight = itemView.right - xMarkMargin
                    val xMarkTop = itemView.top + (itemHeight - intrinsicHeight) / 2
                    val xMarkBottom = xMarkTop + intrinsicHeight
                    xMark!!.setBounds(xMarkLeft, xMarkTop, xMarkRight, xMarkBottom)
                    xMark!!.draw(c)
                    super.onChildDraw(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
            }
        val mItemTouchHelper = ItemTouchHelper(simpleItemTouchCallback)
        mItemTouchHelper.attachToRecyclerView(mRecyclerView)
    }

    /**
     * We're gonna setup another ItemDecorator that will draw the red background in the empty space while the items are animating to thier new positions
     * after an item is removed.
     */
    private fun setUpAnimationDecoratorHelper() {
        mRecyclerView!!.addItemDecoration(object : ItemDecoration() {
            // we want to cache this and not allocate anything repeatedly in the onDraw method
            var background: Drawable? = null
            var initiated = false
            private fun init() {
                background = ColorDrawable(Color.RED)
                initiated = true
            }

            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                if (!initiated) {
                    init()
                }

                // only if animation is in progress
                if (parent.itemAnimator!!.isRunning) {

                    // some items might be animating down and some items might be animating up to close the gap left by the removed item
                    // this is not exclusive, both movement can be happening at the same time
                    // to reproduce this leave just enough items so the first one and the last one would be just a little off screen
                    // then remove one from the middle

                    // find first child with translationY > 0
                    // and last one with translationY < 0
                    // we're after a rect that is not covered in recycler-view views at this point in time
                    var lastViewComingDown: View? = null
                    var firstViewComingUp: View? = null

                    // this is fixed
                    val left = 0
                    val right = parent.width

                    // this we need to find out
                    var top = 0
                    var bottom = 0

                    // find relevant translating views
                    val childCount = parent.layoutManager!!.childCount
                    for (i in 0 until childCount) {
                        val child = parent.layoutManager!!.getChildAt(i)
                        if (child!!.translationY < 0) {
                            // view is coming down
                            lastViewComingDown = child
                        } else if (child.translationY > 0) {
                            // view is coming up
                            if (firstViewComingUp == null) {
                                firstViewComingUp = child
                            }
                        }
                    }
                    if (lastViewComingDown != null && firstViewComingUp != null) {
                        // views are coming down AND going up to fill the void
                        top = lastViewComingDown.bottom + lastViewComingDown.translationY.toInt()
                        bottom = firstViewComingUp.top + firstViewComingUp.translationY.toInt()
                    } else if (lastViewComingDown != null) {
                        // views are going down to fill the void
                        top = lastViewComingDown.bottom + lastViewComingDown.translationY.toInt()
                        bottom = lastViewComingDown.bottom
                    } else if (firstViewComingUp != null) {
                        // views are coming up to fill the void
                        top = firstViewComingUp.top
                        bottom = firstViewComingUp.top + firstViewComingUp.translationY.toInt()
                    }
                    background!!.setBounds(left, top, right, bottom)
                    background!!.draw(c)
                }
                super.onDraw(c, parent, state)
            }
        })
    }

    /**
     * RecyclerView adapter enabling undo on a swiped away item.
     */
    internal inner class TestAdapter : RecyclerView.Adapter<TestViewHolder>() {
        var items: MutableList<String> = ArrayList()
        var itemsPendingRemoval: MutableList<String> = ArrayList()
        private var lastInsertedIndex: Int =
            15 // so we can add some more items for testing purposes

        var isUndoOn = false // is undo on, you can turn it on from the toolbar menu

        private val handler = Handler() // handler for running delayed runnables
        private var pendingRunnables =
            HashMap<String, Runnable>() // map of items to pending runnables, so we can cancel a removal if need be

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
            return TestViewHolder(parent)
        }

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
            val viewHolder = holder as TestViewHolder?
            val item = items[position]
            if (itemsPendingRemoval.contains(item)) {
                // we need to show the "undo" state of the row
                viewHolder!!.itemView.setBackgroundColor(Color.RED)
                viewHolder.titleTextView.visibility = View.GONE
                viewHolder.undoButton.visibility = View.VISIBLE
                viewHolder.undoButton.setOnClickListener { // user wants to undo the removal, let's cancel the pending task
                    val pendingRemovalRunnable = pendingRunnables[item]
                    pendingRunnables.remove(item)
                    if (pendingRemovalRunnable != null) handler.removeCallbacks(
                        pendingRemovalRunnable
                    )
                    itemsPendingRemoval.remove(item)
                    // this will rebind the row in "normal" state
                    notifyItemChanged(items.indexOf(item))
                }
            } else {
                // we need to show the "normal" state
                viewHolder!!.itemView.setBackgroundColor(Color.WHITE)
                viewHolder.titleTextView.visibility = View.VISIBLE
                viewHolder.titleTextView.text = item
                viewHolder.undoButton.visibility = View.GONE
                viewHolder.undoButton.setOnClickListener(null)
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }

        /**
         * Utility method to add some rows for testing purposes. You can add rows from the toolbar menu.
         */
        fun addItems(howMany: Int) {
            if (howMany > 0) {
                for (i in lastInsertedIndex + 1..lastInsertedIndex + howMany) {
                    items.add("Item $i")
                    notifyItemInserted(items.size - 1)
                }
                lastInsertedIndex += howMany
            }
        }

        fun pendingRemoval(position: Int) {
            val item = items[position]
            if (!itemsPendingRemoval.contains(item)) {
                itemsPendingRemoval.add(item)
                // this will redraw row in "undo" state
                notifyItemChanged(position)
                // let's create, store and post a runnable to remove the item
                val pendingRemovalRunnable = Runnable { remove(items.indexOf(item)) }
                handler.postDelayed(
                    pendingRemovalRunnable,
                    PENDING_REMOVAL_TIMEOUT.toLong()
                )
                pendingRunnables[item] = pendingRemovalRunnable
            }
        }

        fun remove(position: Int) {
            val item = items[position]
            if (itemsPendingRemoval.contains(item)) {
                itemsPendingRemoval.remove(item)
            }
            if (items.contains(item)) {
                items.removeAt(position)
                notifyItemRemoved(position)
            }
        }

        fun isPendingRemoval(position: Int): Boolean {
            val item = items[position]
            return itemsPendingRemoval.contains(item)
        }

        init {
            // let's generate some items
            // this should give us a couple of screens worth
            for (i in 1..lastInsertedIndex) {
                items.add("Item $i")
            }
        }
    }

    /**
     * ViewHolder capable of presenting two states: "normal" and "undo" state.
     */
    internal class TestViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.row_view, parent, false)
    ) {
        var titleTextView: TextView = itemView.findViewById<View>(R.id.title_text_view) as TextView
        var undoButton: Button = itemView.findViewById<View>(R.id.undo_button) as Button

    }

    companion object {
        private const val PENDING_REMOVAL_TIMEOUT = 3000 // 3sec
    }
}